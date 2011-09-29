/*
 * This file is provided to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.basho.riak.client.itest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.basho.riak.client.IRiakClient;
import com.basho.riak.client.IRiakObject;
import com.basho.riak.client.RiakException;
import com.basho.riak.client.RiakRetryFailedException;
import com.basho.riak.client.bucket.Bucket;
import com.basho.riak.client.cap.Quora;
import com.basho.riak.client.cap.UnresolvedConflictException;
import com.basho.riak.client.convert.ConversionException;
import com.basho.riak.client.convert.PassThroughConverter;
import com.basho.riak.client.operations.FetchObject;
import com.basho.riak.client.raw.Transport;
import com.basho.riak.client.util.CharsetUtils;

/**
 * @author russell
 * 
 */
public abstract class ITestClientBasic {

    protected IRiakClient client;

    @Before public void setUp() throws RiakException {
        this.client = getClient();
    }

    /**
     * @return an {@link IRiakClient} implementation for the transport to be
     *         tested.
     */
    protected abstract IRiakClient getClient() throws RiakException;

    /**
     * @return the number of times a get/put/delete cycle must be performed for
     *         a very high probability of actually seeing a tombstone vclock.
     */
    protected abstract int getDeletedVClockLoopTimes();

    @Test public void ping() throws RiakException {
        client.ping();
    }

    @Test public void fetchBucket() throws RiakException {
        final String bucketName = UUID.randomUUID().toString();

        Bucket b = client.fetchBucket(bucketName).execute();

        assertNotNull(b);
        assertEquals(bucketName, b.getName());
        assertEquals(new Integer(3), b.getNVal());
        assertFalse(b.getAllowSiblings());
    }

    @Test public void updateBucket() throws RiakException {
        final String bucketName = UUID.randomUUID().toString();

        Bucket b = client.fetchBucket(bucketName).execute();

        assertNotNull(b);
        assertEquals(bucketName, b.getName());
        assertEquals(new Integer(3), b.getNVal());
        assertFalse(b.getAllowSiblings());

        b = client.updateBucket(b).nVal(4).allowSiblings(true).execute();

        assertNotNull(b);
        assertEquals(bucketName, b.getName());
        assertEquals(new Integer(4), b.getNVal());
        assertTrue(b.getAllowSiblings());
    }

    @Test public void createBucket() throws RiakException {
        final String bucketName = UUID.randomUUID().toString();

        Bucket b = client.createBucket(bucketName).nVal(1).allowSiblings(true).execute();

        assertNotNull(b);
        assertEquals(bucketName, b.getName());
        assertEquals(new Integer(1), b.getNVal());
        assertTrue(b.getAllowSiblings());
    }

    @Test public void clientIds() throws Exception {
        final byte[] clientId = CharsetUtils.utf8StringToBytes("abcd");

        client.setClientId(clientId.clone());
        assertArrayEquals(clientId, client.getClientId());

        byte[] newId = client.generateAndSetClientId();

        assertArrayEquals(newId, client.getClientId());
    }

    @Test public void listBuckets() throws RiakException {
        final String bucket1 = UUID.randomUUID().toString();
        final String bucket2 = UUID.randomUUID().toString();

        Bucket b1 = client.createBucket(bucket1).execute();
        Bucket b2 = client.createBucket(bucket2).execute();

        b1.store("key", "value").execute();
        b2.store("key", "value").execute();

        Set<String> buckets = client.listBuckets();

        assertTrue("Expected bucket 1 to be present", buckets.contains(bucket1));
        assertTrue("Expected bucket 2 to be present", buckets.contains(bucket2));
    }

    // full set of bucket properties
    @Test public void allBucketProperties() throws Exception {
        final String bucket = UUID.randomUUID().toString();

        client.createBucket(bucket).allowSiblings(true).lastWriteWins(false)
            .smallVClock(5).bigVClock(20).youngVClock(40).oldVClock(172800)
                .nVal(2).r(1).w(1).dw(Quora.ONE).rw(Quora.QUORUM).pr(1).pw(1).notFoundOK(false).basicQuorum(true)
                .enableForSearch()
                .execute();

        Bucket b2 = client.fetchBucket(bucket).execute();

        assertEquals(true, b2.getAllowSiblings());
        assertEquals(new Integer(2), b2.getNVal());

        // HTTP only below
        Assume.assumeTrue(Transport.HTTP.equals(client.getTransport()));
        assertEquals(false, b2.getLastWriteWins());
        assertEquals(new Integer(5), b2.getSmallVClock());
        assertEquals(new Integer(20), b2.getBigVClock());
        assertEquals(new Long(40), b2.getYoungVClock());
        assertEquals(new Long(172800), b2.getOldVClock());
        assertEquals(1, b2.getR().getIntValue());
        assertEquals(1, b2.getW().getIntValue());
        assertEquals(Quora.ONE, b2.getDW().getSymbolicValue());
        assertEquals(Quora.QUORUM, b2.getRW().getSymbolicValue());
        assertEquals(1, b2.getPR().getIntValue());
        assertEquals(1, b2.getPW().getIntValue());
        assertTrue(b2.getBasicQuorum());
        assertFalse(b2.getNotFoundOK());
        assertTrue(b2.getSearch());
    }

    @Test public void conditionalFetch() throws Exception {
        final String bucket = UUID.randomUUID().toString();
        final String key = "key";
        final String originalValue = "first_value";
        final String newValue = "second_value";

        Bucket b = client.fetchBucket(bucket).execute();
        b.store(key, originalValue).execute();

        IRiakObject obj = b.fetch(key).execute();

        assertNotNull(obj);
        assertEquals(originalValue, obj.getValueAsString());

        final FetchObject<IRiakObject> fo = b.fetch(key)
            .ifModified(obj.getVClock()) // in case of PB
            .modifiedSince(obj.getLastModified()); // in case of HTTP

        IRiakObject obj2 = fo.execute();

        assertNull(obj2);
        assertTrue(fo.isUnmodified());

        // wait because of coarseness of last modified time update (HTTP).
        Thread.sleep(1000);
        // change it, fetch it
        obj.setValue(newValue);
        b.store(obj).withConverter(new PassThroughConverter()).execute();

        IRiakObject obj3 = b.fetch(key)
            .ifModified(obj.getVClock()) // in case of PB
            .modifiedSince(obj.getLastModified()) // in case of HTTP
            .execute();

        assertNotNull(obj3);
        assertEquals(newValue, obj3.getValueAsString());
    }

    @Test public void deletedVclock() throws Exception {
        final String bucket = UUID.randomUUID().toString();
        final String key = "k";

        final Bucket b = client.fetchBucket(bucket).execute();

        final CountDownLatch endLatch = new CountDownLatch(1);

        final Runnable putter = new Runnable() {

            public void run() {
                int cnt = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        b.store(key, String.valueOf(cnt)).execute();
                    } catch (RiakException e) {
                        // no-op, keep going
                    }
                }
            }
        };

        final Thread putterThread = new Thread(putter);

        final Runnable deleter = new Runnable() {

            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        b.delete(key).execute();
                    } catch (RiakException e) {
                        // no-op, keep going
                    }
                }
            }
        };

        final Thread deleterThread = new Thread(deleter);

        final Runnable getter = new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        FetchObject<IRiakObject> fo = b.fetch(key).returnDeletedVClock(true);
                        IRiakObject o = fo.execute();

                        if (fo.hasDeletedVclock()) {
                            endLatch.countDown();
                            Thread.currentThread().interrupt();
                            putterThread.interrupt();
                            deleterThread.interrupt();
                            assertNull(o);

                        }
                    } catch (RiakException e) {
                        // no-op, keep going
                    }
                }
            }
        };

        final Thread getterThread = new Thread(getter);

        putterThread.start();
        deleterThread.start();
        getterThread.start();

        boolean sawDeletedVClock = endLatch.await(10, TimeUnit.SECONDS);

        if (!sawDeletedVClock) {
            putterThread.interrupt();
            getterThread.interrupt();
            deleterThread.interrupt();
        } else {
            assertTrue(sawDeletedVClock); // somewhat redundant, but it's a
                                          // test.
        }
    }
}