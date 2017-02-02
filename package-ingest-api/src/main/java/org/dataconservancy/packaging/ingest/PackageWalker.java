/*
 * Copyright 2017 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.ingest;

/**
 * Walks resources in some implementation-specific way, and deposits them.
 *
 * @author apb@jhu.edu
 */
public interface PackageWalker {

    /**
     * Deposit resources using the given depositor and notifier.
     *
     * @param depositor The walker will use this to deposit.
     * @param notifier Notifications of deposit shall be sent here.
     */
    public void walk(Depositor depositor, DepositNotifier notifier);
}
