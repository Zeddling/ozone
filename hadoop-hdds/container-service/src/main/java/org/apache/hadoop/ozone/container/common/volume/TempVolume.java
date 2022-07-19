package org.apache.hadoop.ozone.container.common.volume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Represents tmp volume for storing containers to
 * be deleted.
 *
 * */
public class TempVolume extends StorageVolume {

    public static final String TEMP_VOLUME_DIR = "temp";

    private final VolumeType type = VolumeType.TEMP_VOLUME;

    protected TempVolume(Builder b) throws IOException {
        super(b);
    }

    public VolumeType getType() {
        return type;
    }

    public static class Builder extends StorageVolume.Builder<Builder> {
        public Builder(String volumeRootStr) {
            super(volumeRootStr, TEMP_VOLUME_DIR);
        }

        @Override
        public Builder getThis() {
            return this;
        }

        public TempVolume build() throws IOException {
            return new TempVolume(this);
        }
    }

}
