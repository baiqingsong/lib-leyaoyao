package com.dawn.lyy;

/**
 * 乐摇摇支付配置类
 * 用于封装支付所需的所有配置参数，替代原来的静态全局变量，避免线程安全问题。
 */
public class PayConfig {

    private final String payUrl;
    private final int payPort;
    private final String appId;
    private final String appSecret;
    private final String deviceId;
    private volatile int price;
    private volatile int inventory;

    private PayConfig(Builder builder) {
        this.payUrl = builder.payUrl;
        this.payPort = builder.payPort;
        this.appId = builder.appId;
        this.appSecret = builder.appSecret;
        this.deviceId = builder.deviceId;
        this.price = builder.price;
        this.inventory = builder.inventory;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public int getPayPort() {
        return payPort;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getInventory() {
        return inventory;
    }

    public void setInventory(int inventory) {
        this.inventory = inventory;
    }

    public static class Builder {
        private String payUrl = "";
        private int payPort = 0;
        private String appId = "";
        private String appSecret = "";
        private String deviceId = "";
        private int price = 0;
        private int inventory = 0;

        public Builder payUrl(String payUrl) {
            this.payUrl = payUrl;
            return this;
        }

        public Builder payPort(int payPort) {
            this.payPort = payPort;
            return this;
        }

        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public Builder appSecret(String appSecret) {
            this.appSecret = appSecret;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder price(int price) {
            this.price = price;
            return this;
        }

        public Builder inventory(int inventory) {
            this.inventory = inventory;
            return this;
        }

        public PayConfig build() {
            if (payUrl == null || payUrl.isEmpty()) {
                throw new IllegalArgumentException("payUrl不能为空");
            }
            if (appId == null || appId.isEmpty()) {
                throw new IllegalArgumentException("appId不能为空");
            }
            if (appSecret == null || appSecret.isEmpty()) {
                throw new IllegalArgumentException("appSecret不能为空");
            }
            if (deviceId == null || deviceId.isEmpty()) {
                throw new IllegalArgumentException("deviceId不能为空");
            }
            return new PayConfig(this);
        }
    }
}
