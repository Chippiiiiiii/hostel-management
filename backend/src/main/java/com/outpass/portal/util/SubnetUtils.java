package com.outpass.portal.util;

public class SubnetUtils {

    public static boolean isInSubnet(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String subnetIp = parts[0];
            int prefixLength = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;

            long ipLong = ipToLong(ip);
            long subnetLong = ipToLong(subnetIp);
            long mask = prefixLength == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

            return (ipLong & mask) == (subnetLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isInAnySubnet(String ip, String commaSeparatedCidrs) {
        if (commaSeparatedCidrs == null || commaSeparatedCidrs.isBlank()) return false;
        for (String cidr : commaSeparatedCidrs.split(",")) {
            if (isInSubnet(ip, cidr.trim())) return true;
        }
        return false;
    }

    private static long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        }
        return result;
    }
}
