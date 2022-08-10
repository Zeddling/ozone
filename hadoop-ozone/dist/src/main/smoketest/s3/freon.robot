# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

*** Settings ***
Documentation       S3 gateway test with aws cli
Library             OperatingSystem
Library             String
Library             BuiltIn
Resource            ./commonawslib.robot

*** Keywords ***
#   Export access key and secret to the environment
Setup aws credentials
    ${accessKey} =   Execute     aws configure get aws_access_key_id
    ${secret} =      Execute     aws configure get aws_secret_access_key
    Set Environment Variable    AWS_SECRET_ACCESS_KEY  ${secret}
    Set Environment Variable    AWS_ACCESS_KEY_ID  ${accessKey}

Default setup
    Setup v4 headers

Freon S3BG
    [arguments]    ${prefix}=s3bg    ${n}=1    ${threads}=1   ${args}=${EMPTY}
    ${result} =        Execute          ozone freon s3bg -t ${threads} -n ${n} -p ${prefix} ${args}
                       Should contain   ${result}   Successful executions: ${n}

*** Test Cases ***
Check setup
    Default Setup

Export AWS credentials
    Setup aws credentials

Run Freon S3BG
    Freon S3BG


