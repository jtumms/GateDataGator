package com.tummsmedia;

public class GateStatusObject {

    public int id;
    public String gateName;
    public String modTime;
    public int totalCount;


    public GateStatusObject() {
    }

    public GateStatusObject(int id, String gateName, String modTime, int totalCount) {
        this.id = id;
        this.gateName = gateName;
        this.modTime = modTime;
        this.totalCount = totalCount;
    }

    public GateStatusObject(String gateName, String modTime) {
        this.gateName = gateName;
        this.modTime = modTime;
    }

    public GateStatusObject(String gateName, String modTime, int totalCount) {
        this.gateName = gateName;
        this.modTime = modTime;
        this.totalCount = totalCount;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getGateName() {
        return gateName;
    }

    public void setGateName(String gateName) {
        this.gateName = gateName;
    }

    public String getModTime() {
        return modTime;
    }

    public void setModTime(String modTime) {
        this.modTime = modTime;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}

