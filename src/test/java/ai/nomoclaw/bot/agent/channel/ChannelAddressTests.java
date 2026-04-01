package ai.nomoclaw.bot.agent.channel;

import ai.nomoclaw.bot.channel.model.ChannelAddress;
import ai.nomoclaw.bot.channel.model.ChannelType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChannelAddressTests {

    @Test
    void shouldParseWebhookAddress() {
        ChannelAddress address = ChannelAddress.parse(ChannelType.FEISHU, "https://example.com/webhook");
        Assertions.assertEquals("webhook", address.kind());
        Assertions.assertEquals("https://example.com/webhook", address.target());
    }

    @Test
    void shouldParsePrefixedAddress() {
        ChannelAddress address = ChannelAddress.parse(ChannelType.DINGTALK, "dingtalk:session:abc");
        Assertions.assertEquals("session", address.kind());
        Assertions.assertEquals("abc", address.target());
    }
}
