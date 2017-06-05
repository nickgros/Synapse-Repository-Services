CREATE TABLE IF NOT EXISTS `ACCESS_REQUIREMENT_REVISION` (
  `OWNER_ID` bigint(20) NOT NULL,
  `NUMBER` bigint(20) NOT NULL,
  `MODIFIED_BY` bigint(20) NOT NULL,
  `MODIFIED_ON` bigint(20) NOT NULL,
  `SERIALIZED_ENTITY` mediumblob,
  PRIMARY KEY (`OWNER_ID`,`NUMBER`),
  CONSTRAINT `ACCESS_REQUIREMENT_REVISION_OWNER_FK` FOREIGN KEY (`OWNER_ID`) REFERENCES `ACCESS_REQUIREMENT` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `ACCESS_REQUIREMENT_REVISION_MODIFIED_BY_FK` FOREIGN KEY (`MODIFIED_BY`) REFERENCES `JDOUSERGROUP` (`ID`)
)