/*
 *
 *  * Copyright 2018 Nikita Shakarun
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package org.microemu.cldc.btl2cap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import org.microemu.microedition.io.ConnectionImplementation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.L2CAPConnectionNotifier;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

public class Connection implements ConnectionImplementation, L2CAPConnection, L2CAPConnectionNotifier {
	private BluetoothServerSocket serverSocket = null;
	public BluetoothSocket socket = null;
	public javax.bluetooth.UUID connUuid = null;
	private boolean skipAfterWrite = false;
	private OutputStream os;
	private InputStream is;

	@Override
	public int getTransmitMTU() throws IOException {
		return L2CAPConnection.DEFAULT_MTU;
	}

	@Override
	public int getReceiveMTU() throws IOException {
		return L2CAPConnection.DEFAULT_MTU;
	}

	@Override
	public void send(byte[] data) throws IOException {
		os.write(data);
	}

	@Override
	public int receive(byte[] inBuf) throws IOException {
		return is.read(inBuf);
	}

	@Override
	public boolean ready() throws IOException {
		return true;
	}

	public javax.microedition.io.Connection openConnection(String name, int mode, boolean timeouts) throws IOException {
		if (name == null)
			throw new IllegalArgumentException("URL is null");
		System.out.println("***** Connection URL: " + name);

		int port = -1;
		int portSepIndex = name.lastIndexOf(':');
		if (portSepIndex == -1) {
			throw new IllegalArgumentException("Port missing");
		}
		String host = name.substring("btl2cap://".length(), portSepIndex);

		int argsStart = name.indexOf(";");
		String[] args = name.substring(argsStart + 1).split(";");
		boolean authenticate = false, encrypt = false, secure;
		String srvname = "";
		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith("authenticate="))
				authenticate = Boolean.parseBoolean(args[i].substring(args[i].indexOf("=") + 1));
			if (args[i].startsWith("encrypt="))
				encrypt = Boolean.parseBoolean(args[i].substring(args[i].indexOf("=") + 1));
			if (args[i].startsWith("name="))
				srvname = args[i].substring(args[i].indexOf("=") + 1);
			if (args[i].startsWith("skipAfterWrite="))
				skipAfterWrite = Boolean.parseBoolean(args[i].substring(args[i].indexOf("=") + 1));
		}
		secure = authenticate && encrypt;

		String uuid = name.substring(portSepIndex + 1, argsStart);
		connUuid = new javax.bluetooth.UUID(uuid, false);
		java.util.UUID btUuid = connUuid.uuid;

		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter.isDiscovering())
			adapter.cancelDiscovery();
		// java.util.UUID btUuid = getJavaUUID(uuid);
		// "localhost" indicates that we are acting as server
		if (host.equals("localhost")) {
			// btUuid = new javax.bluetooth.UUID(0x1101).uuid;

			// Android 6.0.1 bug: UUID is reversed
			// see https://issuetracker.google.com/issues/37075233
			if (secure)
				serverSocket = adapter.listenUsingRfcommWithServiceRecord(srvname, btUuid);
			else
				serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(srvname, btUuid);
		} else {
			StringBuilder sb = new StringBuilder(host);
			for (int i = 2; i < sb.length(); i += 3)
				sb.insert(i, ':');
			String addr = sb.toString();

			BluetoothDevice dev = adapter.getRemoteDevice(addr);
			if (secure)
				socket = dev.createRfcommSocketToServiceRecord(btUuid);
			else
				socket = dev.createInsecureRfcommSocketToServiceRecord(btUuid);

			try {
				socket.connect();
			} catch (IOException e) {
			}
		}
		return this;
	}

	@Override
	public L2CAPConnection acceptAndOpen() throws IOException {
		if (serverSocket != null) {
			socket = serverSocket.accept();
			serverSocket.close();
			serverSocket = null;
		}
		return this;
	}

	@Override
	public void close() throws IOException {
		if (serverSocket != null)
			serverSocket.close();
		if (socket != null)
			socket.close();
	}
}