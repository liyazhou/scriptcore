package connectors.mongodb.codec;

import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

public class BaseObjectCodecProvider implements CodecProvider {
    public BaseObjectCodecProvider() {
	}

	@Override
    @SuppressWarnings("unchecked")
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (BaseObject.class.isAssignableFrom(clazz)) {
            return (Codec<T>) new BaseObjectCodec(clazz, registry);
        }
        return null;
    }
}