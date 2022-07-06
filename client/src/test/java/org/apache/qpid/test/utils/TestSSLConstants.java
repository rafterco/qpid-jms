/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.qpid.test.utils;

public interface TestSSLConstants
{
    String KEYSTORE = "java_client_keystore.jks";
    String UNTRUSTED_KEYSTORE = "java_client_untrusted_keystore.jks";
    String KEYSTORE_PASSWORD = "password";
    String TRUSTSTORE = "java_client_truststore.jks";
    String TRUSTSTORE_PASSWORD = "password";

    String CERT_ALIAS_APP1 = "app1";
    String CERT_ALIAS_APP2 = "app2";
    String CERT_ALIAS_UNTRUSTED_CLIENT = "untrusted_client";

    String BROKER_PEERSTORE = "java_broker_peerstore.jks";
    String BROKER_PEERSTORE_PASSWORD = "password";

    String BROKER_TRUSTSTORE = "java_broker_truststore.jks";
    String BROKER_TRUSTSTORE_PASSWORD = "password";
}
