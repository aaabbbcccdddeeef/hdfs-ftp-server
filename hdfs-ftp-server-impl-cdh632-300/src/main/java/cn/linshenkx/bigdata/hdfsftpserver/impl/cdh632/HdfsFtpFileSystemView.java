package cn.linshenkx.bigdata.hdfsftpserver.impl.cdh632;


import cn.linshenkx.bigdata.hdfsftpserver.common.HadoopProp;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class HdfsFtpFileSystemView implements FileSystemView {

    private final HadoopProp hadoopProp;

    private static final Configuration CONF = new Configuration();

    private static final Logger log = LoggerFactory.getLogger(HdfsFtpFileSystemView.class);

    /**
     * current user work directory
     */
    public String current = "/";
    public FileSystem dfs = null;
    private User user = null;

    public HdfsFtpFileSystemView(User user, HadoopProp prop) {
        log.info("PROCESS|FtpFileSystemView|initialize");
        hadoopProp = prop;
        if (dfs == null) {
            init();
            try {
                dfs = FileSystem.get(CONF);
                this.user = user;
                log.info("SUCCESS|FtpFileSystemView|initialize");
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("ERROR|FtpFileSystemView|initialize", e);
            }
        }
    }


    /**
     * 加载初始化配置
     * 登录kerberos用户
     */
    public void init() {
        File hadoopConfDir = new File(hadoopProp.getConfDir());
        if (!hadoopConfDir.exists()) {
            throw new RuntimeException("HadoopConfDir is error:" + hadoopProp.getConfDir());
        }
        File[] hadoopConfArray = hadoopConfDir.listFiles();
        for (File file : hadoopConfArray) {
            if (file.getName().endsWith(".xml")) {
                CONF.addResource(new Path(file.getAbsolutePath()));
            }
        }
        CONF.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        CONF.set("dfs.client.failover.max.attempts", "3");
        if ("kerberos".equals(CONF.get("hadoop.security.authentication"))) {
            try {
                LoginUtil.setJaasConf("Client", hadoopProp.getUserPrincipal(), hadoopProp.getUserKeytab());
                LoginUtil.login(hadoopProp.getUserPrincipal(), hadoopProp.getUserKeytab(), hadoopProp.getKrb5Conf(), CONF);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            CONF.set("HADOOP_USER_NAME", user == null ? "ftp" : user.getName());
        }
    }


    @Override
    public void dispose() {
        if (dfs == null) {
            return;
        }
        try {
            dfs.close();
        } catch (IOException e) {
            e.printStackTrace();
            log.info("ERROR|FtpFileSystemView|dispose");
        }
    }

    @Override
    public boolean changeWorkingDirectory(String path) throws FtpException {
        if (path.startsWith("/")) {
            current = path;
        } else if ("..".equals(path)) {
            current = current.substring(0, current.lastIndexOf("/"));
            if (current.equals("")) {
                current = "/";
            }
        } else if (current.endsWith("/")) {
            current = current + path;
        } else {
            current = current + "/" + path;
        }
        return true;
    }

    @Override
    public FtpFile getFile(String file) {
        String path = "";
        if (file.startsWith("/")) {
            path = file;
        } else if (file.equals("./")) {
            path = current;
        } else if (current.equals("/")) {
            path = current + file;
        } else {
            path = current + "/" + file;
        }
        return new HdfsFtpFile(path, this);
    }

    @Override
    public FtpFile getHomeDirectory() throws FtpException {
        return new HdfsFtpFile("/", this);
    }

    @Override
    public FtpFile getWorkingDirectory() throws FtpException {
        return new HdfsFtpFile(current, this);
    }

    @Override
    public boolean isRandomAccessible() throws FtpException {
        return true;
    }

}
