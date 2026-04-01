package ai.nomoclaw.bot.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nomoclaw")
public class NomoClawProperties {

    private String rootDir = System.getProperty("user.home") + "/" + NomoClawPaths.ROOT_DIR_NAME;

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }
}
