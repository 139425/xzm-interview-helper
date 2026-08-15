package com.xzm.xzm_interview_helper.serveragent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerCommandPolicyTest {
    private final ServerCommandPolicy policy = new ServerCommandPolicy();

    @Test
    void allowsSimpleReadOnlyDiagnostics() {
        assertThat(policy.classify("uptime")).isEqualTo(ServerRisk.READ_ONLY);
        assertThat(policy.classify("systemctl status nginx")).isEqualTo(ServerRisk.READ_ONLY);
        assertThat(policy.classify("df -h")).isEqualTo(ServerRisk.READ_ONLY);
    }

    @Test
    void unknownAndMutatingCommandsNeedApproval() {
        assertThat(policy.classify("touch /www/wwwroot/ready")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("systemctl restart nginx")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("ls && rm -rf /tmp/example")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("ls -l\ntouch /www/wwwroot/ready")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("ls -l & touch /www/wwwroot/ready")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("ip link set eth0 down")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("ip netns exec demo touch /tmp/ready")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("find /tmp -fprint /tmp/output")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("date -s '2030-01-01'")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("hostname replacement")).isEqualTo(ServerRisk.DANGEROUS);
        assertThat(policy.classify("rg --pre 'touch /tmp/ready' needle .")).isEqualTo(ServerRisk.DANGEROUS);
    }

    @Test
    void blocksCredentialReadsInsteadOfOfferingApproval() {
        assertThat(policy.classify("cat /root/.ssh/id_rsa")).isEqualTo(ServerRisk.BLOCKED);
        assertThat(policy.classify("printenv DB_PASSWORD")).isEqualTo(ServerRisk.BLOCKED);
        assertThat(policy.classify("cat /proc/1/environ")).isEqualTo(ServerRisk.BLOCKED);
        assertThat(policy.classify("grep -o . /proc/1/envir*")).isEqualTo(ServerRisk.BLOCKED);
    }
}
