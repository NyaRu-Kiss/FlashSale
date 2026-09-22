CREATE TABLE IF NOT EXISTS xxl_job_lock (lock_name VARCHAR(50) NOT NULL PRIMARY KEY);
INSERT IGNORE INTO xxl_job_lock(lock_name) VALUES ('schedule_lock');
CREATE TABLE IF NOT EXISTS xxl_job_group (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, app_name VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL, title VARCHAR(64), address_type TINYINT NOT NULL DEFAULT 0,
  address_list TEXT, access_token VARCHAR(255), update_time DATETIME, UNIQUE KEY idx_app_name(app_name)
);
CREATE TABLE IF NOT EXISTS xxl_job_info (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, job_group INT NOT NULL,
  name VARCHAR(255), job_desc VARCHAR(255), add_time DATETIME, update_time DATETIME,
  author VARCHAR(64), alarm_email VARCHAR(255), schedule_type VARCHAR(50) NOT NULL,
  schedule_conf VARCHAR(128), misfire_strategy VARCHAR(50) NOT NULL,
  executor_route_strategy VARCHAR(50), executor_handler VARCHAR(255), executor_param VARCHAR(512),
  executor_block_strategy VARCHAR(50), executor_timeout INT NOT NULL DEFAULT 0, executor_fail_retry_count INT NOT NULL DEFAULT 0,
  executor_fail_strategy VARCHAR(50), glue_type VARCHAR(50) NOT NULL,
  glue_source MEDIUMTEXT, glue_remark VARCHAR(128), glue_updatetime DATETIME,
  child_jobid VARCHAR(255), trigger_status TINYINT NOT NULL DEFAULT 0,
  trigger_last_time BIGINT NOT NULL DEFAULT 0, trigger_next_time BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY idx_job_group(job_group, job_desc)
);
CREATE TABLE IF NOT EXISTS xxl_job_user (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL,
  password VARCHAR(50) NOT NULL, role TINYINT NOT NULL, permission VARCHAR(255),
  UNIQUE KEY idx_username(username)
);
CREATE TABLE IF NOT EXISTS xxl_job_registry (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, registry_group VARCHAR(50) NOT NULL,
  registry_key VARCHAR(255) NOT NULL, registry_value VARCHAR(255) NOT NULL, update_time DATETIME,
  UNIQUE KEY idx_registry(registry_group, registry_key, registry_value)
);
CREATE TABLE IF NOT EXISTS xxl_job_logglue (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, job_id INT NOT NULL, glue_type VARCHAR(50),
  glue_source MEDIUMTEXT, glue_remark VARCHAR(128) NOT NULL, add_time DATETIME, update_time DATETIME
);
CREATE TABLE IF NOT EXISTS xxl_job_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, job_group INT NOT NULL, job_id INT NOT NULL,
  executor_address VARCHAR(255), executor_handler VARCHAR(255), executor_param TEXT,
  executor_sharding_param VARCHAR(20), executor_fail_retry_count INT NOT NULL DEFAULT 0,
  trigger_time DATETIME, trigger_code INT NOT NULL, trigger_msg TEXT, handle_time DATETIME,
  handle_code INT NOT NULL, handle_msg TEXT, alarm_status TINYINT NOT NULL DEFAULT 0,
  KEY idx_trigger_time(trigger_time), KEY idx_handle_code(handle_code)
);
CREATE TABLE IF NOT EXISTS xxl_job_log_report (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, trigger_day DATETIME, running_count INT NOT NULL DEFAULT 0,
  suc_count INT NOT NULL DEFAULT 0, fail_count INT NOT NULL DEFAULT 0, update_time DATETIME,
  UNIQUE KEY idx_trigger_day(trigger_day)
);
