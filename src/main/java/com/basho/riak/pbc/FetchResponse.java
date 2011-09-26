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
package com.basho.riak.pbc;

/**
 * Encapsulates greater detail about the result of a fetch.
 * 
 * @author russell
 * 
 */
public class FetchResponse {

    private final RiakObject[] objects;
    private final boolean unchanged;

    /**
     * @param objects
     *            the set of riak sibling values
     * @param unchanged
     *            is this an 'unchanged' response to a conditional fetch
     */
    protected FetchResponse(RiakObject[] objects, boolean unchanged) {
        this.objects = objects;
        this.unchanged = unchanged;
    }

    /**
     * @return the objects
     */
    public RiakObject[] getObjects() {
        return objects;
    }

    /**
     * @return true if Riak PB API returned unchanged in response to a
     *         conditional fetch, false otherwise
     */
    public boolean isUnchanged() {
        return unchanged;
    }

}
