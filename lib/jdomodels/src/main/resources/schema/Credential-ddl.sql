CREATE TABLE `CREDENTIAL` (
  `PRINCIPAL_ID` bigint(20) NOT NULL,
  `PASS_HASH` varchar(73) CHARACTER SET latin1 COLLATE latin1_bin DEFAULT NULL,
  `SECRET_KEY` varchar(100) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
  PRIMARY KEY (`PRINCIPAL_ID`), 
  CONSTRAINT `PRINCIPAL_ID_FK` FOREIGN KEY (`PRINCIPAL_ID`) REFERENCES `JDOUSERGROUP` (`ID`) ON DELETE CASCADE
)