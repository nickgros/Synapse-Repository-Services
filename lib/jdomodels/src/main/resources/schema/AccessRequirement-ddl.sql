CREATE TABLE IF NOT EXISTS `ACCESS_REQUIREMENT` (
  `ID` bigint(20) NOT NULL,
  `ETAG` char(36) NOT NULL,
  `CURRENT_REV_NUM` bigint(20) DEFAULT 0,
  `CREATED_BY` bigint(20) NOT NULL,
  `CREATED_ON` bigint(20) NOT NULL,
  `ACCESS_TYPE` ENUM('DOWNLOAD', 'PARTICIPATE') NOT NULL,
  `CONCRETE_TYPE` varchar(100) CHARACTER SET latin1 COLLATE latin1_bin,
  PRIMARY KEY (`ID`),
  CONSTRAINT `ACCESS_REQUIREMENT_CREATED_BY_FK` FOREIGN KEY (`CREATED_BY`) REFERENCES `JDOUSERGROUP` (`ID`) ON DELETE CASCADE
)
