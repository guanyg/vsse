# Achieve Efficient and Verifiable Conjunctive and Fuzzy Queries over Encrypted Data in Cloud

The test client for Android devices see [Github:guanyg/vsse-android](https://github.com/guanyg/vsse-android).

## Test Server

### Build
```bash
cd test_server
mvn assembly:single
```

### Run
```bash
java -jar target/test-server-1.0-SNAPSHOT-jar-with-dependencies.jar \
    -d db.properties \
    -c credential.bin \
    -f ciphers/ 
```

Database configuration for result storage. 

*db.properties*
```properties
driverClass=com.mysql.jdbc.Driver
url=jdbc:mysql://<host>:<port>/<database>
username=<username>
password=<password>
```

SQL to create the result table:
```iso92-sql
DROP TABLE IF EXISTS result;
CREATE TABLE result ( 
    tcid int, -- 1 
    t_keyword_cnt int, -- 2 
    t_document_cnt int, -- 3 
    radix_tree longblob, -- 4 
    keyword varchar(500), -- 5 
    keyword_cnt int, -- 6 
    prefix_len int, -- 7 
    tail_len int, -- 8 
    type varchar(5), -- 9 
    query longblob, -- 10 
    response longblob, -- 11 
    search_time int, -- 12 
    verify_time_pc int, -- 13 
    verify_time_and int, -- 14 
    run varchar(30), -- 15 
    t timestamp, -- 16 
    node_cnt int(11), -- 17
    primary key(run, tcid), 
    index(t_keyword_cnt), 
    index(t_document_cnt), 
    index(run) 
);
``` 


## Test Client

### Build
```bash
cd test_client
mvn assembly:single
```

### Run

```bash
java -jar target/test-client-1.0-SNAPSHOT-jar-with-dependencies.jar \
    -h <server-ip> -n `uname -n`
```
