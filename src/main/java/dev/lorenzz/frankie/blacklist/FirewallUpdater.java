package dev.lorenzz.frankie.blacklist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Simple firewall updater that blocks an IP using iptables.
 * In production consider using a proper firewall API or exec with sudo.
 */
public class FirewallUpdater {

    /**
     * Blocks the given IP address by adding a DROP rule to INPUT chain.
     * Assumes the process has sufficient privileges.
     */
    public void blockIp(String ip) {
        String[] cmd = {"/sbin/iptables", "-A", "INPUT", "-s", ip, "-j", "DROP"};
        try {
            Process process = new ProcessBuilder(cmd).start();
            // Wait for completion and log output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Firewall] " + line);
                }
                while ((line = errorReader.readLine()) != null) {
                    System.err.println("[Firewall Error] " + line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("[Firewall] iptables exited with code " + exitCode);
            } else {
                System.out.println("[Firewall] Blocked IP " + ip);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to execute iptables", e);
        }
    }

    /** Optionally unblock (for testing) */
    public void unblockIp(String ip) {
        String[] cmd = {"/sbin/iptables", "-D", "INPUT", "-s", ip, "-j", "DROP"};
        try {
            Process process = new ProcessBuilder(cmd).start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to unblock IP", e);
        }
    }
}