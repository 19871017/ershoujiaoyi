package com.secondhand.platform.modules.auth.application;

@FunctionalInterface
public interface IpLocationResolver {
    String resolve(String clientIp);
}
