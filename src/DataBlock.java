import java.io.IOException;
import java.io.OutputStream;

/**
 * Class for dealing with bit vectors. Has support for putting data in and out,
 * and outputting to an output stream.
 * 
 * @author E
 */
public class DataBlock {
	/**
	 * Contains our data. Think of the newer data being stored in the less
	 * significant bits.
	 */
	public long data;
	/** Number of bits we're storing */
	public int length;

	/**
	 * Initialises an empty DataBlock.
	 */
	public DataBlock() {
		this.data = 0;
		this.length = 0;
	}

	/**
	 * Creates DataBlock with the first length bits of data.
	 * 
	 * @param data data
	 * @param length number of bits
	 */
	public DataBlock(long data, int length) {
		this.data = data & ((1 << length) - 1);
		this.length = length;
	}

	/**
	 * Appends d and flushes any full bytes to out.
	 * 
	 * @param d data block to append
	 * @param out stream to flush to
	 * @throws IOException
	 */
	public void appendAndFlush(DataBlock d, OutputStream out) throws IOException {
		flush(out, false);
		pushLeft(d);
		flush(out, false);
	}

	/**
	 * @return copy of this DataBlock
	 */
	public DataBlock copy() {
		return new DataBlock(data, length);
	}

	/**
	 * Writes any full bytes (starting from the less significant bits) to out. If
	 * all is true, writes an incomplete byte, padding with zeroes.
	 * 
	 * @param out output stream to write to
	 * @param all whether to flush incomplete bytes
	 * @throws IOException
	 */
	public void flush(OutputStream out, boolean all) throws IOException {
		while (true) {
			// If there are no bytes, or there isn't a full byte and we aren't writing
			// partial bytes, break.
			if (length == 0 || (length < 8 && !all)) {
				break;
			}
			int sz = Math.min(8, length);
			out.write((int) (pullRight(sz).data << (8 - sz)));
		}
	}

	/**
	 * Returns the first sz lsb and removes them.
	 * 
	 * @param sz number of bits to pull out
	 * @return DataBlock with the data pulled out
	 */
	public DataBlock pullLeft(int sz) {
		long val = data & ((1 << sz) - 1);
		length -= sz;
		data >>>= sz;
		return new DataBlock(val, sz);
	}

	/**
	 * Returns the first sz msb and removes them.
	 * 
	 * @param sz number of bits to pull out
	 * @return DataBlock with the data pulled out
	 */
	public DataBlock pullRight(int sz) {
		long val = data >>> (length - sz);
		length -= sz;
		data &= ((1 << length) - 1);
		return new DataBlock(val, sz);
	}

	/**
	 * Adds the data in d, in the lsb.
	 * 
	 * @param d data block to append
	 * @throws IllegalArgumentException
	 */
	public void pushLeft(DataBlock d) throws IllegalArgumentException {
		if (length + d.length > 64) {
			throw new IllegalArgumentException();
		}
		data <<= d.length;
		data |= d.data;
		length += d.length;
	}

	/**
	 * See pushLeft.
	 */
	public void pushLeft(long d, int l) {
		pushLeft(new DataBlock(d, l));
	}

	/**
	 * Adds the data in d, in the msb.
	 * 
	 * @param d data block to append
	 * @throws IllegalArgumentException
	 */
	public void pushRight(DataBlock d) throws IllegalArgumentException {
		if (length + d.length > 64) {
			throw new IllegalArgumentException();
		}

		data |= d.data << length;
		length += d.length;
	}

	/**
	 * See pushRight.
	 */
	public void pushRight(long d, int l) {
		pushRight(new DataBlock(d, l));
	}
}
