package se.exuvo.aurora.utils

import java.io.ByteArrayOutputStream
import java.util.Arrays

class OutputStreamListener: ByteArrayOutputStream() {
	var output = ""
	private var oldCount = 0
	private var oldLastRowIndex = -1

	override fun flush() {
		if (count > oldCount) {

			var end = count
			var lastRowIndex = count - 1

			while (lastRowIndex > 0) {
				if (buf[lastRowIndex] == '\n'.toByte()) {

					if (end == count) {
						end = lastRowIndex - 1

					} else if (end - lastRowIndex > 1) {
						lastRowIndex++
						break;
					}
				}

				lastRowIndex--
			}

			if (lastRowIndex > oldLastRowIndex) {

				output = String(Arrays.copyOfRange(buf, lastRowIndex, end));
				oldLastRowIndex = lastRowIndex
			}

			oldCount = count
		}
	}
	
	override fun toString() = output
}