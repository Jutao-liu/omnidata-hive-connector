package org.apache.hadoop.hive.ql.omnidata.reader;

import static org.apache.hadoop.hive.ql.omnidata.status.NdpStatusManager.NDP_DATANODE_HOSTNAME_SEPARATOR;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.omnidata.config.NdpConf;
import org.apache.hadoop.hive.ql.omnidata.operator.enums.NdpEngineEnum;
import org.apache.hadoop.hive.ql.omnidata.status.NdpStatusManager;
import org.apache.hadoop.mapred.FileSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * OmniDataProperty
 */
public class OmniDataProperty {
    private static final Logger LOGGER = LoggerFactory.getLogger(OmniDataProperty.class);

    private final String defaultPartitionValues;

    private Properties properties = new Properties();

    private List<String> omniDataHosts;

    private String port;

    private NdpConf ndpconf;

    public OmniDataProperty(Configuration conf, FileSplit fileSplit) {
        this.defaultPartitionValues = HiveConf.getVar(conf, HiveConf.ConfVars.DEFAULTPARTITIONNAME);
        this.ndpconf = new NdpConf(conf);
        this.omniDataHosts = getOmniDataHosts(conf, fileSplit);
        this.properties.put("grpc.ssl.enabled", ndpconf.getNdpGrpcSslEnabled());
        this.properties.put("grpc.client.cert.file.path", ndpconf.getNdpGrpcClientCertFilePath());
        this.properties.put("grpc.client.private.key.file.path", ndpconf.getNdpGrpcClientPrivateKeyFilePath());
        this.properties.put("grpc.trust.ca.file.path", ndpconf.getNdpGrpcTrustCaFilePath());
        this.properties.put("pki.dir", ndpconf.getNdpPkiDir());
        this.properties.put("rpc.sdi.port", ndpconf.getNdpSdiPort());
        this.port = ndpconf.getNdpSdiPort();
    }

    private List<String> getOmniDataHosts(Configuration conf, FileSplit fileSplit) {
        List<String> hosts = new ArrayList<>();
        String engine = HiveConf.getVar(conf, HiveConf.ConfVars.HIVE_EXECUTION_ENGINE).toLowerCase(Locale.ENGLISH);
        if (engine.equals(NdpEngineEnum.Tez.getEngine())) {
            if (conf.get(NdpStatusManager.NDP_TEZ_DATANODE_HOSTNAMES) != null) {
                List<String> dataNodeHosts = new ArrayList<>(Arrays.asList(
                    conf.get(NdpStatusManager.NDP_TEZ_DATANODE_HOSTNAMES).split(NDP_DATANODE_HOSTNAME_SEPARATOR)));
                // If the number of nodes is less than 3, add available datanode
                if (dataNodeHosts.size() < ndpconf.getNdpReplicationNum()) {
                    addDataNodeHosts(conf, fileSplit, dataNodeHosts);
                }
                dataNodeHosts.forEach(dn -> {
                    // possibly null
                    if (conf.get(dn) != null) {
                        hosts.add(conf.get(dn));
                    }
                });
            }

            // add a random available datanode
            String randomHost = NdpStatusManager.getRandomAvailableDataNodeHost(conf, hosts);
            if (randomHost.length() > 0) {
                hosts.add(conf.get(randomHost));
            }
            return hosts;
        } else if (engine.equals(NdpEngineEnum.MR.getEngine())) {
            String[] dataNodeHosts = conf.get(fileSplit.getPath().toUri().getPath())
                .split(NDP_DATANODE_HOSTNAME_SEPARATOR);
            Arrays.asList(dataNodeHosts).forEach(dn -> {
                // possibly null
                if (conf.get(dn) != null) {
                    hosts.add(conf.get(dn));
                }
            });
            // add a random available host
            String randomHost = NdpStatusManager.getRandomAvailableDataNodeHost(conf, hosts);
            if (randomHost.length() > 0) {
                hosts.add(conf.get(randomHost));
            }
            return hosts;
        } else {
            throw new UnsupportedOperationException(String.format("Engine [%s] is not supported", engine));
        }
    }

    private void addDataNodeHosts(Configuration conf, FileSplit fileSplit, List<String> hosts) {
        try {
            BlockLocation[] blockLocations = fileSplit.getPath()
                .getFileSystem(conf)
                .getFileBlockLocations(fileSplit.getPath(), fileSplit.getStart(), fileSplit.getLength());
            for (BlockLocation block : blockLocations) {
                for (String host : block.getHosts()) {
                    if (hosts.size() == ndpconf.getNdpReplicationNum()) {
                        return;
                    }
                    if (!hosts.contains(host)) {
                        hosts.add(host);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("OmniDataProperty getHostsByPath() failed", e);
        }
    }

    public Properties getProperties() {
        return properties;
    }

    public String getDefaultPartitionValues() {
        return defaultPartitionValues;
    }

    public List<String> getOmniDataHosts() {
        return omniDataHosts;
    }

    public String getPort() {
        return port;
    }
}

