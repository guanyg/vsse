# Achieve Efficient and Verifiable Conjunctive and Fuzzy Queries over Encrypted Data in Cloud

The test client for Android devices see [Github:guanyg/vsse-android](https://github.com/guanyg/vsse-android).

## Build
```bash
mvn install
```

## Test Server

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
DROP TABLE IF EXISTS `result`;
CREATE TABLE `result` (
  `tcid` int(11) NOT NULL,
  `t_keyword_cnt` int(11) DEFAULT NULL,
  `t_document_cnt` int(11) DEFAULT NULL,
  `radix_tree` longblob,
  `keyword` varchar(500) DEFAULT NULL,
  `keyword_cnt` int(11) DEFAULT NULL,
  `prefix_len` int(11) DEFAULT NULL,
  `tail_len` int(11) DEFAULT NULL,
  `type` varchar(5) DEFAULT NULL,
  `query` longblob,
  `response` longblob,
  `search_time` int(11) DEFAULT NULL,
  `verify_time_pc` int(11) DEFAULT NULL,
  `verify_time_and` int(11) DEFAULT NULL,
  `run` varchar(30) NOT NULL,
  `t` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `node_cnt` int(11) DEFAULT NULL,
  `success_lst_size` int(11) DEFAULT NULL,
  `failed_lst_size` int(11) DEFAULT NULL,
  `file_cnt` int(11) DEFAULT NULL,
  PRIMARY KEY (`run`,`tcid`),
  KEY `t_keyword_cnt` (`t_keyword_cnt`),
  KEY `t_document_cnt` (`t_document_cnt`),
  KEY `run` (`run`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
``` 


## Test Client

### Run

```bash
java -jar target/test-client-1.0-SNAPSHOT-jar-with-dependencies.jar \
    -h <server-ip> -n `uname -n`
```
