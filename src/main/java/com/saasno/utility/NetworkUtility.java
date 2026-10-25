package com.saasno.utility;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

// Matches a client IP against a list of IPs and CIDR ranges (IPv4 and IPv6).
public final class NetworkUtility {

	private NetworkUtility() {
	}

	// True when the list is empty (no restriction) or the address is inside one of its entries. Unparseable addresses never match.
	public static boolean allowed(String address, String allowlist) {
		List<String> entries = entries(allowlist);
		if (entries.isEmpty()) {
			return true;
		}
		byte[] ip = parse(address);
		if (ip == null) {
			return false;
		}
		for (String entry : entries) {
			if (contains(entry, ip)) {
				return true;
			}
		}
		return false;
	}

	public static List<String> entries(String allowlist) {
		List<String> list = new ArrayList<>();
		if (allowlist != null) {
			for (String part : allowlist.split("[,\\s]+")) {
				if (!part.isBlank()) {
					list.add(part.trim());
				}
			}
		}
		return list;
	}

	// An entry is valid when it is an IP or "IP/prefix" with a prefix that fits the address family.
	public static boolean validEntry(String entry) {
		int slash = entry.indexOf('/');
		byte[] ip = parse(slash < 0 ? entry : entry.substring(0, slash));
		if (ip == null) {
			return false;
		}
		if (slash < 0) {
			return true;
		}
		try {
			int prefix = Integer.parseInt(entry.substring(slash + 1));
			return prefix >= 0 && prefix <= ip.length * 8;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean contains(String entry, byte[] ip) {
		int slash = entry.indexOf('/');
		byte[] network = parse(slash < 0 ? entry : entry.substring(0, slash));
		if (network == null || network.length != ip.length) {
			return false;
		}
		int prefix = ip.length * 8;
		if (slash >= 0) {
			try {
				prefix = Integer.parseInt(entry.substring(slash + 1));
			} catch (NumberFormatException e) {
				return false;
			}
			if (prefix < 0 || prefix > ip.length * 8) {
				return false;
			}
		}
		for (int i = 0; i < prefix; i++) {
			int mask = 0x80 >> (i % 8);
			if ((ip[i / 8] & mask) != (network[i / 8] & mask)) {
				return false;
			}
		}
		return true;
	}

	// Literal addresses only: never resolves a hostname, so a header value cannot trigger a DNS lookup.
	private static byte[] parse(String address) {
		if (address == null) {
			return null;
		}
		String value = address.trim();
		if (value.isEmpty() || !value.matches("[0-9a-fA-F:.]+")) {
			return null;
		}
		try {
			// Java turns an IPv4-mapped IPv6 address ("::ffff:1.2.3.4") into plain IPv4, so it matches IPv4 entries.
			return InetAddress.getByName(value).getAddress();
		} catch (UnknownHostException e) {
			return null;
		}
	}

}
