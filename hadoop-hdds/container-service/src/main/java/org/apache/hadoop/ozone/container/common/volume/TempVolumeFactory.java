package org.apache.hadoop.ozone.container.common.volume;

import org.apache.hadoop.fs.StorageType;
import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.fs.SpaceUsageCheckFactory;

import java.io.IOException;

/**
 * A factory class for TempVolume
 * */
public class TempVolumeFactory extends StorageVolumeFactory {

    public TempVolumeFactory(ConfigurationSource conf,
         SpaceUsageCheckFactory usageCheckFactory, MutableVolumeSet volumeSet,
         String datanodeUuid, String clusterID) {
        super(conf, usageCheckFactory, volumeSet, datanodeUuid, clusterID);
    }

    @Override
    StorageVolume createVolume(String locationString,
       StorageType storageType) throws IOException {
        TempVolume.Builder volumeBuilder = new TempVolume.Builder(locationString)
                .conf(getConf())
                .datanodeUuid(getDatanodeUuid())
                .clusterID(getClusterID())
                .usageCheckFactory(getUsageCheckFactory())
                .storageType(storageType)
                .volumeSet(getVolumeSet());
        TempVolume volume = volumeBuilder.build();

        checkAndSetClusterID(volume.getClusterID());

        return volume;
    }

    @Override
    StorageVolume createFailedVolume(String locationString) throws IOException {
        TempVolume.Builder volumeBuilder = new TempVolume.Builder(locationString)
                .failedVolume(true);

        return volumeBuilder.build();
    }
}
