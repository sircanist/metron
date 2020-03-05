/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.metron.dataloads.nonbulk.geo;


import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.metron.common.configuration.ConfigurationsUtils;
import org.apache.metron.common.utils.CompressionStrategies;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.enrichment.adapters.maxmind.asn.GeoLiteAsnDatabase;
import org.apache.metron.enrichment.adapters.maxmind.geo.GeoLiteCityDatabase;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public class MaxmindDbEnrichmentLoader {

  private static final String DEFAULT_RETRIES = "2";
  private static final String DEFAULT_GEOIP_CITY_EDITION = "GeoLite2-City";
  private static final String DEFAULT_GEOIP_ASN_EDITION = "GeoLite2-ASN";
  private static final String DEFAULT_GEOIP_BASE_URL = "https://download.maxmind.com/app/geoip_download";
  private static final String DEFAULT_GEOIP_SUFFIX = "tar.gz";

  private static abstract class OptionHandler implements Function<String, Option> {
  }

  public enum GeoEnrichmentOptions {
    HELP("h", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        return new Option(s, "help", false, "Generate Help screen");
      }
    }),
    GEO_LICENCE("l", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "geoip_licence", true, "GeoIP Licence");
        o.setArgName("GEOIP_LICENCE");
        o.setRequired(false);
        return o;
      }
    }),
    GEO_CITY_EDITION("ce", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "geoip_city_edition", true, "GeoIP City Edition - defaults to " + DEFAULT_GEOIP_CITY_EDITION);
        o.setArgName("GEOIP_CITY_EDITION");
        o.setRequired(false);
        return o;
      }
    }),
    GEO_ASN_EDITION("ae", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "geoip_asn_edition", true, "GeoIP ASN Edition - defaults to " + DEFAULT_GEOIP_ASN_EDITION);
        o.setArgName("GEOIP_ASN_EDITION");
        o.setRequired(false);
        return o;
      }
    }),
    GEO_BASE_URL("b", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "geoip_base_url", true, "GeoIP Base URL - defaults to " + DEFAULT_GEOIP_BASE_URL);
        o.setArgName("GEOIP_BASE_URL");
        o.setRequired(false);
        return o;
      }
    }),
    GEO_SUFFIX("s", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "geoip_suffix", true, "GeoIP suffix - defaults to " + DEFAULT_GEOIP_SUFFIX);
        o.setArgName("GEOIP_BASE_URL");
        o.setRequired(false);
        return o;
      }
    }),
    ASN_URL("a", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "asn_url", true, "GeoIP City URL");
        o.setArgName("ASN_URL");
        o.setRequired(false);
        return o;
      }
    }),
    GEO_URL("g", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "geo_url", true, "GeoIP ASN URL");
        o.setArgName("GEO_URL");
        o.setRequired(false);
        return o;
      }
    }),
    REMOTE_GEO_DIR("r", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "remote_dir", true, "HDFS directory to land formatted GeoLite2 City file - defaults to /apps/metron/geo/<epoch millis>/");
        o.setArgName("REMOTE_DIR");
        o.setRequired(false);
        return o;
      }
    }),
    REMOTE_ASN_DIR("ra", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "remote_asn_dir", true, "HDFS directory to land formatted GeoLite2 ASN file - defaults to /apps/metron/asn/<epoch millis>/");
        o.setArgName("REMOTE_DIR");
        o.setRequired(false);
        return o;
      }
    }),
    RETRIES("re", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "retries", true, "Number of GeoLite2 database download retries, after an initial failure.");
        o.setArgName("RETRIES");
        o.setRequired(false);
        return o;
      }
    }),
    TMP_DIR("t", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "tmp_dir", true, "Directory for landing the temporary GeoLite2 data - defaults to /tmp");
        o.setArgName("TMP_DIR");
        o.setRequired(false);
        return o;
      }
    }),
    ZK_QUORUM("z", new MaxmindDbEnrichmentLoader.OptionHandler() {
      @Override
      public Option apply(@Nullable String s) {
        Option o = new Option(s, "zk_quorum", true, "Zookeeper Quorum URL (zk1:port,zk2:port,...)");
        o.setArgName("ZK_QUORUM");
        o.setRequired(true);
        return o;
      }
    });
    Option option;
    String shortCode;

    GeoEnrichmentOptions(String shortCode, MaxmindDbEnrichmentLoader.OptionHandler optionHandler) {
      this.shortCode = shortCode;
      this.option = optionHandler.apply(shortCode);
    }

    public boolean has(CommandLine cli) {
      return cli.hasOption(shortCode);
    }

    public String get(CommandLine cli) {
      return cli.getOptionValue(shortCode);
    }

    public String get(CommandLine cli, String defaultValue) {
      return cli.getOptionValue(shortCode, defaultValue);
    }

    public static CommandLine parse(CommandLineParser parser, String[] args) {
      try {
        CommandLine cli = parser.parse(getOptions(), args);

        if (MaxmindDbEnrichmentLoader.GeoEnrichmentOptions.HELP.has(cli)) {
          printHelp();
          System.exit(0);
        }
        return cli;
      } catch (ParseException e) {
        System.err.println("Unable to parse args: " + Joiner.on(' ').join(args));
        e.printStackTrace(System.err);
        printHelp();
        System.exit(-1);
        return null;
      }
    }

    public static void printHelp() {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("MaxmindDbEnrichmentLoader", getOptions());
    }

    public static Options getOptions() {
      Options ret = new Options();
      for (MaxmindDbEnrichmentLoader.GeoEnrichmentOptions o : MaxmindDbEnrichmentLoader.GeoEnrichmentOptions.values()) {
        ret.addOption(o.option);
      }
      return ret;
    }
  }

  protected void loadGeoLiteDatabase(CommandLine cli) throws IOException {
    File localGeoFile;
    File localASNFile;

    int numRetries = Integer.parseInt(GeoEnrichmentOptions.RETRIES.get(cli, DEFAULT_RETRIES));

    String tmpDir = GeoEnrichmentOptions.TMP_DIR.get(cli, "/tmp") + "/"; // Make sure there's a file separator at the end
    String geo_url = GeoEnrichmentOptions.GEO_URL.get(cli, null);
    String asn_url = GeoEnrichmentOptions.ASN_URL.get(cli, null);
    String geo_suffix = GeoEnrichmentOptions.GEO_SUFFIX.get(cli, DEFAULT_GEOIP_SUFFIX);
    String geo_base_url = GeoEnrichmentOptions.GEO_BASE_URL.get(cli, DEFAULT_GEOIP_BASE_URL);

    String licence = GeoEnrichmentOptions.GEO_LICENCE.get(cli, null);
    if ((geo_url == null || asn_url == null) && licence == null) {
      System.err.println("Either licence or geoip url is required.");
      System.exit(6);
    }

    if (geo_url == null) {
      String geo_city_edition = GeoEnrichmentOptions.GEO_CITY_EDITION.get(cli, DEFAULT_GEOIP_CITY_EDITION);
      geo_url = geo_base_url + "?edition=" + geo_city_edition + "&licence=" + licence + "&suffix=" + geo_suffix;
      localGeoFile = new File(tmpDir + geo_city_edition + "." + geo_suffix);
    } else {
      localGeoFile = new File(tmpDir + new File(new URL(geo_url).getPath()).getName());
    }

    if (asn_url == null) {
      String geo_asn_edition = GeoEnrichmentOptions.GEO_CITY_EDITION.get(cli, DEFAULT_GEOIP_CITY_EDITION);
      asn_url = geo_base_url + "?edition=" + geo_asn_edition + "&licence=" + licence + "&suffix=" + geo_suffix;
      localASNFile = new File(tmpDir + geo_asn_edition + "." + geo_suffix);
    } else {
      localASNFile = new File(tmpDir + new File(new URL(geo_url).getPath()).getName());
    }

    try {
      localGeoFile = downloadGeoFile(geo_url, localGeoFile, numRetries);
      localASNFile = downloadGeoFile(asn_url, localASNFile, numRetries);
    } catch (IllegalStateException ies) {
      System.err.println("Failed to download geo db file. Aborting");
      System.exit(5);
    }

    // Want to delete the tar in event of failure
    localGeoFile.deleteOnExit();
    localASNFile.deleteOnExit();
    System.out.println("GeoIP city db downloaded successfully");

    // Push the Geo file to HDFS and update Configs to ensure clients get new view
    String zookeeper = GeoEnrichmentOptions.ZK_QUORUM.get(cli);
    long millis = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
    String hdfsGeoLoc = GeoEnrichmentOptions.REMOTE_GEO_DIR.get(cli, "/apps/metron/geo/" + millis);
    System.out.println("Putting GeoLite City file into HDFS at: " + hdfsGeoLoc);
    String hdfsAsnLoc = GeoEnrichmentOptions.REMOTE_ASN_DIR.get(cli, "/apps/metron/asn/" + millis);
    System.out.println("Putting ASN file into HDFS at: " + hdfsAsnLoc);

    // Put Geo into HDFS
    Path srcPath = new Path(localGeoFile.getAbsolutePath());
    Path dstPath = new Path(hdfsGeoLoc);
    putDbFile(srcPath, dstPath);
    pushConfig(srcPath, dstPath, GeoLiteCityDatabase.GEO_HDFS_FILE, zookeeper);

    // Put ASN into HDFS
    srcPath = new Path(localASNFile.getAbsolutePath());
    dstPath = new Path(hdfsAsnLoc);
    putDbFile(srcPath, dstPath);
    pushConfig(srcPath, dstPath, GeoLiteAsnDatabase.ASN_HDFS_FILE, zookeeper);

    System.out.println("GeoLite2 file placement complete");
    System.out.println("Successfully created and updated new GeoLite information");
  }

  protected File downloadGeoFile(String urlStr, File localFile, int numRetries) {
    int attempts = 0;
    boolean valid = false;
    while (!valid && attempts <= numRetries) {
      try {
        URL url = new URL(urlStr);

        System.out.println("Downloading " + url.toString() + " to " + localFile.getAbsolutePath());
        if (localFile.exists() && !localFile.delete()) {
          System.err.println(
              "File already exists locally and can't be deleted.  Please delete before continuing");
          System.exit(3);
        }
        FileUtils.copyURLToFile(url, localFile, 5000, 10000);
        if (!CompressionStrategies.GZIP.test(localFile)) {
          throw new IOException("Invalid Gzip file");
        } else {
          valid = true;
        }
      } catch (MalformedURLException e) {
        System.err.println("Malformed URL - aborting: " + e);
        e.printStackTrace();
        System.exit(4);
      } catch (IOException e) {
        System.err.println("Warning: Unable to copy remote GeoIP database to local file, attempt " + attempts + ": " + e);
        e.printStackTrace();
      }
      attempts++;
    }
    if (!valid) {
      System.err.println("Unable to copy remote GeoIP database to local file after " + attempts + " attempts");
      throw new IllegalStateException("Unable to download geo enrichment database.");
    }
    return localFile;
  }

  protected void pushConfig(Path srcPath, Path dstPath, String configName, String zookeeper) {
    System.out.println("Beginning update of global configs");
    try (CuratorFramework client = ConfigurationsUtils.getClient(zookeeper)) {
      client.start();
      // Use the parent and place a new file.  Has to be a new file so we can update the configs and trigger updates.
      // Fetch the global configuration
      Map<String, Object> global = JSONUtils.INSTANCE.load(
              new ByteArrayInputStream(ConfigurationsUtils.readGlobalConfigBytesFromZookeeper(client)),
              JSONUtils.MAP_SUPPLIER);

      // Update the global config and push it back
      global.put(configName, dstPath.toString() + "/" + srcPath.getName());
      ConfigurationsUtils.writeGlobalConfigToZookeeper(global, client);
    } catch (Exception e) {
      System.err.println("Unable to load new GeoLite2 config for " + configName + " into HDFS: " + e);
      e.printStackTrace();
      System.exit(2);
    }
    System.out.println("Finished update of global configs");
  }

  protected void putDbFile(Path src, Path dst) throws IOException {
    Configuration conf = new Configuration();
    FileSystem fileSystem = FileSystem.get(conf);
    System.out.println("Putting: " + src + " onto HDFS at: " + dst);
    fileSystem.mkdirs(dst);
    fileSystem.copyFromLocalFile(true, true, src, dst);
    System.out.println("Done putting GeoLite file into HDFS");
  }

  public static void main(String... argv) throws IOException {
    String[] otherArgs = new GenericOptionsParser(argv).getRemainingArgs();
    CommandLine cli = GeoEnrichmentOptions.parse(new PosixParser(), otherArgs);

    MaxmindDbEnrichmentLoader loader = new MaxmindDbEnrichmentLoader();
    loader.loadGeoLiteDatabase(cli);
  }
}
