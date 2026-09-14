package com.redis.config;

import java.io.File;

/**
 * Configuration options for the Redis server.
 */
public class ServerConfig {
    private String host = "0.0.0.0";
    private int port = 6379;
    private String dir = ".";
    private String dbFileName = "dump.rdb";
    private String aofFileName = "appendonly.aof";
    private boolean appendOnly = true;
    private String aofFsync = "everysec";
    private String requirePass = null;
    private int dbCount = 16;
    private int timeout = 0; // 0 = no timeout

    public static ServerConfig parse(String[] args) {
        ServerConfig config = new ServerConfig();
        if (args == null) return config;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                arg = arg.substring(2);
            } else if (arg.startsWith("-")) {
                arg = arg.substring(1);
            }

            if (i + 1 < args.length) {
                String val = args[i + 1];
                if (arg.equalsIgnoreCase("port") || arg.equalsIgnoreCase("p")) {
                    config.setPort(Integer.parseInt(val));
                    i++;
                } else if (arg.equalsIgnoreCase("host") || arg.equalsIgnoreCase("bind") || arg.equalsIgnoreCase("h")) {
                    config.setHost(val);
                    i++;
                } else if (arg.equalsIgnoreCase("dir")) {
                    config.setDir(val);
                    i++;
                } else if (arg.equalsIgnoreCase("dbfilename")) {
                    config.setDbFileName(val);
                    i++;
                } else if (arg.equalsIgnoreCase("appendfilename")) {
                    config.setAofFileName(val);
                    i++;
                } else if (arg.equalsIgnoreCase("appendonly")) {
                    config.setAppendOnly("yes".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val));
                    i++;
                } else if (arg.equalsIgnoreCase("appendfsync")) {
                    config.setAofFsync(val);
                    i++;
                } else if (arg.equalsIgnoreCase("requirepass")) {
                    config.setRequirePass(val);
                    i++;
                } else if (arg.equalsIgnoreCase("databases")) {
                    config.setDbCount(Integer.parseInt(val));
                    i++;
                }
            }
        }
        return config;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getDbFileName() {
        return dbFileName;
    }

    public void setDbFileName(String dbFileName) {
        this.dbFileName = dbFileName;
    }

    public String getAofFileName() {
        return aofFileName;
    }

    public void setAofFileName(String aofFileName) {
        this.aofFileName = aofFileName;
    }

    public boolean isAppendOnly() {
        return appendOnly;
    }

    public void setAppendOnly(boolean appendOnly) {
        this.appendOnly = appendOnly;
    }

    public String getAofFsync() {
        return aofFsync;
    }

    public void setAofFsync(String aofFsync) {
        this.aofFsync = aofFsync;
    }

    public String getRequirePass() {
        return requirePass;
    }

    public void setRequirePass(String requirePass) {
        this.requirePass = requirePass;
    }

    public int getDbCount() {
        return dbCount;
    }

    public void setDbCount(int dbCount) {
        this.dbCount = dbCount;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public File getRdbFile() {
        return new File(dir, dbFileName);
    }

    public File getAofFile() {
        return new File(dir, aofFileName);
    }
}
