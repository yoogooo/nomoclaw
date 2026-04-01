package ai.nomoclaw.bot.channel.stream;

public interface ChannelStreamConnector {

    String name();

    void start();

    void stop();
}
