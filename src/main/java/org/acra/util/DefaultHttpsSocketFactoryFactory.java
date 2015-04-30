package org.acra.util;

import org.apache.http.conn.scheme.SocketFactory;

import android.content.Context;

/**
 * Default implementation of a HttpSocketFactoryFactory.
 */
public final class DefaultHttpsSocketFactoryFactory implements
		HttpsSocketFactoryFactory {

	public static final HttpsSocketFactoryFactory INSTANCE = new DefaultHttpsSocketFactoryFactory();

	@Override
	public SocketFactory create(Context context) {
		return new TlsSniSocketFactory();
	}
}
