<!--

    Copyright (c) 2007-2014 Sonatype, Inc. All rights reserved.

    This program is licensed to you under the Apache License Version 2.0,
    and you may not use this file except in compliance with the Apache License Version 2.0.
    You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.

    Unless required by applicable law or agreed to in writing,
    software distributed under the Apache License Version 2.0 is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.

-->
# Nexus Copy From Plugin

Example plugin for "Copying artifacts between local repositories".

Example session, copying artifact from one repository to another:

###URL Template: 
http://localhost:8081/nexus/service/local/repositories/<<targetRepository>>/copyfrom

####Example copy from using GET. Copies one artifact.
```curl
curl -H "Accept:application/json" http://localhost:8081/nexus/service/local/repositories/staging-one/copyfrom?g=com.ibm.informix&a=ifxjdbc&version=4.10.7-SNAPSHOT&r=snapshots
```
{"result":"SUCCESSFUL"}

####Example copy from using PUT.  Copies one artifact.
```curl
curl -H "Accept:application/json" -H "Content-Type:application/json" -X PUT --data-binary '{"groupId":"com.ibm.informix","artifactId":"ifxjdbc","version":"4.10.7-SNAPSHOT","repositoryId":"snapshots"}' http://localhost:8081/nexus/service/local/repositories/staging-one/copyfrom
```

####Example copy from using PUT.  Copies multiple artifacts.
```curl
curl -H "Accept:application/json" -H "Content-Type:application/json" -X PUT --data-binary '[{"groupId":"com.ibm.informix","artifactId":"ifxjdbc","version":"4.10.7-SNAPSHOT","repositoryId":"snapshots"},{"groupId":"com.ibm.informix","artifactId":"ifxjdbcx","version":"4.10.7-SNAPSHOT","repositoryId":"snapshots"}]' http://localhost:8081/nexus/service/local/repositories/staging-one/copyfrom
```


####GET's query parameters
| Query Parameter | Description |
| --------------- | ----------- |
| g | GroupId of the artifact (required) 
| a | ArtifactId of the artifact (required) 
| v | Version of the artifact (required) Supports resolving of "LATEST", "RELEASE" and snapshot versions ("1.0-SNAPSHOT") too. 
| r | RepositoryId of the source repository that the artifact is contained in (required) 
| p | Packaging type of the artifact (optional) 
| c | Classifier of the artifact (optional) 
| e | Extension of the artifact (optional) 

####PUT's json keys
| Key | Description |
| --- | ----------- |
| groupId | GroupId of the artifact (required) 
| artifactId | ArtifactId of the artifact (required) 
| version | Version of the artifact (required) Supports resolving of "LATEST", "RELEASE" and snapshot versions ("1.0-SNAPSHOT") too. 
| repositoryId | RepositoryId of the source repository that the artifact is contained in (required) 
| packaging | Packaging type of the artifact (optional) 
| classifier | Classifier of the artifact (optional) 
| extension | Extension of the artifact (optional) 


###Problems issues:

I tried this plugin on 2.11.2-06 and noticed PUT with an array did not work.  It would copy the frist artifact but I would get a HTTP 500 Internal Server error and the nexus error log said "Caused by: java.nio.file.NoSuchFileException: "Storage directory"/"target repository"/.nexus/tmp/"gav".    Therefore, I switched to running the request in a loop. 

I did not find this issue with 2.12.0-01.   I was able to successfully run a PUT with an array.  
