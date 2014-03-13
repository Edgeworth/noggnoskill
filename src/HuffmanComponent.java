
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Huffman component of NoGGNoSkill. Compresses in 32 KB blocks, using a 2 pass
 * Huffman. The tree is stored efficiently in the file by using a canonical
 * Huffman representation and then compressing that with Huffman. The format
 * ouputted is of the form: <9 bits of header info><compressed header describing
 * the Huffman tree><compressed data using the described Huffman tree,
 * terminated by an end of block symbol>. The last block is terminated by an EOS
 * symbol instead of a EOB symbol.
 * 
 * @author E
 */
public class HuffmanComponent {
	/**
	 * Our alphabet consists of characters [0, 255], 256 (end of block), 257 (end
	 * of stream).
	 */
	private static final int ALPHABET_SIZE = 258;
	/**
	 * We divide our input into blocks for which we generate a Huffman table. This
	 * is used to exploit local trends in data. Our decoder can handle arbitrarily
	 * sized blocks, however. The optimum value was found through empirical
	 * testing.
	 */
	private static final int BLOCK_SIZE = 32 * 1024;
	/**
	 * Pregenerated frequency table from test data for the typical distribution of
	 * symbols in the header. Used to optimise static Huffman compression of the
	 * header.
	 */
	private static final int[] HEADER_INITIAL = { 7856, 67, 4, 60, 115, 177, 305,
	    435, 1789, 1778, 1671, 1339, 1062, 530, 434, 372, 378, 191, 227, 229,
	    162, 131, 94, 166, 36 };

	/**
	 * Since we cannot be sure of how much data we're going to get per call of
	 * decompressAndAppend, we must maintain a lot of state. We use a state
	 * machine type construction to help deal with this.
	 */
	private DecoderState decoderState;
	/**
	 * We must maintain our main block data decoder outside in case we don't get
	 * all of a Huffman block in one call.
	 */
	private HuffmanCoder decoder;
	/** We must maintain the header decoder outside for similar reasons. */
	private HuffmanCoder headerDecoder;
	/** Tells us what symbol we're currently reading from the header. */
	private int headerLoc;
	/** A bit buffer for all data coming into the decoder. */
	private final DataBlock decoderBuf;
	/** A bit buffer for all data coming into the encoder. */
	private final DataBlock encoderBuf;
	/**
	 * Stores the current canonical representations of symbols. This data comes
	 * from the header.
	 */
	private List<CanonicalPair> decoderCanonical;

	/**
	 * Initialise the Huffman component.
	 */
	public HuffmanComponent() {
		this.decoderState = DecoderState.READING_HEADER_INFO;
		this.decoder = null;
		this.headerDecoder = null;
		this.headerLoc = 0;
		this.decoderBuf = new DataBlock();
		this.encoderBuf = new DataBlock();
		this.decoderCanonical = null;
	}

	/**
	 * Divides length bytes from data into blocks, then compresses and writes to
	 * out. If fin is true, the last block written to out will be terminated by
	 * EOS.
	 * 
	 * @param data bytes to compress
	 * @param length number of bytes from data to compress
	 * @param fin if true, terminate with EOS rather than EOB
	 * @param out output stream to write to
	 * @throws IOException
	 */
	public void compressAndWrite(int[] data, int length, boolean fin,
	    OutputStream out) throws IOException {
		int[] subArray = new int[BLOCK_SIZE];
		int numBlocks = length / BLOCK_SIZE + (length % BLOCK_SIZE > 0 ? 1 : 0);
		for (int i = 0; i < numBlocks; ++i) {
			int subArrayLength = Math.min(BLOCK_SIZE, length - i * BLOCK_SIZE);
			System.arraycopy(data, i * BLOCK_SIZE, subArray, 0, subArrayLength);
			// We only want to write EOS if it's the last block.
			compressAndWriteInternal(subArray, subArrayLength, fin
			    && (i == numBlocks - 1), out);
		}
	}

	/**
	 * Does not perform any subdivision into blocks. Takes the data given and
	 * compresses length bytes and writes to out. If fin is true, writes EOS
	 * instead of EOB.
	 * 
	 * @param data bytes to compress
	 * @param length number of bytes from data to compress
	 * @param fin if true, terminate with EOS rather than EOB
	 * @param out output stream to write to
	 * @throws IOException
	 */
	public void compressAndWriteInternal(int[] data, int length, boolean fin,
	    OutputStream out) throws IOException {
		// Perform our first pass on the data to get the frequencies.
		int[] blockFreq = new int[ALPHABET_SIZE];
		for (int i = 0; i < length; ++i) {
			blockFreq[data[i]]++;
		}
		// If we're at the last block, make sure we have an EOS symbol in our
		// Huffman tree.
		if (fin) {
			blockFreq[ALPHABET_SIZE - 1]++;
		} else { // Otherwise make sure we have an EOB symbol.
			blockFreq[ALPHABET_SIZE - 2]++;
		}

		HuffmanNode root = HuffmanNode.generateTree(blockFreq);
		DataBlock[] rep = HuffmanNode.generateRep(root, ALPHABET_SIZE);

		// We want to generate a canonical representation.
		List<CanonicalPair> canonical = new ArrayList<CanonicalPair>(ALPHABET_SIZE);
		for (int i = 0; i < rep.length; ++i) {
			if (rep[i] != null) {
				// If there is a representation for this symbol, add it to our list.
				canonical.add(new CanonicalPair(i, rep[i].length));
			}
		}

		DataBlock[] canonicalRep = generateCanonicalRep(canonical);
		// We are compressing the header (which contains the canonical
		// representation) using static Huffman. But, we only want to have symbols
		// in the Huffman tree up to the maximum bit length, so we output a 9 bit
		// value (our alphabet size is 258 in the worst case).
		int tableSize = canonical.get(canonical.size() - 1).length + 1;
		encoderBuf.appendAndFlush(new DataBlock(tableSize, 9), out);

		int[] headerFreq = new int[tableSize];
		Arrays.fill(headerFreq, 1);
		System.arraycopy(HEADER_INITIAL, 0, headerFreq, 0,
		    Math.min(headerFreq.length, HEADER_INITIAL.length));

		HuffmanNode headerRoot = HuffmanNode.generateTree(headerFreq);
		DataBlock[] headerRep = HuffmanNode.generateRep(headerRoot, tableSize);
		HuffmanCoder headerEncoder = new HuffmanCoder(null, headerRep);

		int[] header = new int[ALPHABET_SIZE];
		for (int i = 0; i < ALPHABET_SIZE; ++i) {
			// We output 0 if there is no representation (i.e. the symbol doesn't
			// occur in the following block).
			header[i] = canonicalRep[i] == null ? 0 : canonicalRep[i].length;
		}
		headerEncoder.compressAndWrite(header, header.length, encoderBuf, out);

		HuffmanCoder encoder = new HuffmanCoder(null, canonicalRep);
		encoder.compressAndWrite(data, length, encoderBuf, out);
		if (fin) {
			encoder.compressAndWrite(new int[] { 257 }, 1, encoderBuf, out);
			// Make sure to flush any left over bits if we won't be called again.
			encoderBuf.flush(out, true);
		} else {
			encoder.compressAndWrite(new int[] { 256 }, 1, encoderBuf, out);
		}
	}

	/**
	 * Takes some amount of data and decompresses it and writes the values to
	 * output. This function is robust to blocks being divided over calls, as long
	 * as all the data is passed in in the correct order in any number of calls to
	 * it, the correct output will be produced. This is done through using a state
	 * machine like construction.
	 * 
	 * @param data data to decompress
	 * @param length number of bytes to take from data
	 * @param output symbols are written to this list
	 */
	public void decompressAndAppend(int[] data, int length, List<Integer> output) {
		// Stores where we are in the array data.
		int idx = 0;
		// We use this to determine if we can safely finish looping if we're at the
		// end of our data. This is necessary because we might have some left over
		// data which can still be processed in decoderBuf and we may not be called
		// again.
		boolean couldNotDoAnything = false;

		while (true) {
			if (idx == length && couldNotDoAnything) {
				break;
			}
			// Try to read some more data.
			idx = fillBuffer(decoderBuf, data, idx, length);

			couldNotDoAnything = true;

			switch (decoderState) {
			// In this state we have already read the header and created the Huffman
			// decoder for the main block, and we're just reading in compressed data
			// and outputting decompressed data.
				case DECODING_BLOCK:
					int out;
					// We keep on reading off symbols from the decoder buffer.
					while ((out = decoder.decompress(decoderBuf)) != -1) {
						couldNotDoAnything = false;
						if (out == ALPHABET_SIZE - 1) {
							decoderState = DecoderState.STREAM_END;
							return;
						} else if (out == ALPHABET_SIZE - 2) {
							decoderState = DecoderState.READING_HEADER_INFO;
							break;
						} else {
							output.add(out);
						}
					}
					break;
				// In this state, we've read the initial header info (telling us how to
				// build the header Huffman tree) and we're reading in canonical
				// representations of symbols.
				case DECODING_HEADER:
					int rep;
					// Try to read in the next bit length if we have still got symbols
					// left.
					while (headerLoc < ALPHABET_SIZE
					    && (rep = headerDecoder.decompress(decoderBuf)) != -1) {
						couldNotDoAnything = false;
						if (rep != 0) {
							decoderCanonical.add(new CanonicalPair(headerLoc, rep));
						}
						headerLoc++;
					}
					if (headerLoc != ALPHABET_SIZE) {
						break;
					}
					// Generate the canonical representation from the bit lengths of the
					// canonical representations of each symbol, and then build the tree
					// from those representations.
					DataBlock[] decoderRep = generateCanonicalRep(decoderCanonical);
					HuffmanNode decoderRoot = HuffmanNode.generateTree(decoderRep);
					decoder = new HuffmanCoder(decoderRoot, decoderRep);
					// We are now ready to decode the block.
					decoderState = DecoderState.DECODING_BLOCK;
					break;
				// In this state we're waiting for enough bits to come in to read the
				// header info.
				case READING_HEADER_INFO:
					if (decoderBuf.length < 9) {
						break;
					}
					couldNotDoAnything = false;
					// This represents the maximum number of bits in our canonical
					// representations, plus one. We use it to build the Huffman tree.
					int headerSize = (int) decoderBuf.pullRight(9).data;
					int[] headerFreq = new int[headerSize];
					Arrays.fill(headerFreq, 1);
					System.arraycopy(HEADER_INITIAL, 0, headerFreq, 0,
					    Math.min(headerFreq.length, HEADER_INITIAL.length));
					HuffmanNode headerRoot = HuffmanNode.generateTree(headerFreq);
					DataBlock[] headerRep = HuffmanNode.generateRep(headerRoot,
					    headerSize);
					// Set the state up for the header decoder.
					headerDecoder = new HuffmanCoder(headerRoot, headerRep);
					headerLoc = 0;
					decoderRep = new DataBlock[ALPHABET_SIZE];
					decoderCanonical = new ArrayList<CanonicalPair>(ALPHABET_SIZE);
					decoderState = DecoderState.DECODING_HEADER;
					break;
				case STREAM_END:
					return;
			}
		}
	}

	/**
	 * Adds bytes from data into buf, if possible, and returns the new index into
	 * data. Will not go past length bytes in data.
	 * 
	 * @param buf bit buffer to add to
	 * @param data array to read out of
	 * @param idx index to next byte to put into buf
	 * @param length constraint on the index
	 * @return new index into data
	 */
	private int fillBuffer(DataBlock buf, int[] data, int idx, int length) {
		if (buf.length < 55 && idx < length) {
			buf.pushLeft(data[idx++], 8);
		}
		return idx;
	}

	/**
	 * Generates the canonical Huffman representation of a list of symbol and bit
	 * length pairs. This allows us to store the Huffman tree more efficiently
	 * since we only need to say what bit length each symbol's representation is.
	 * 
	 * @param canonical list of symbol and bit length pairs
	 * @return map from symbol to canonical representation.
	 */
	private DataBlock[] generateCanonicalRep(List<CanonicalPair> canonical) {
		// Sort by bit length and then alphabetical order.
		Collections.sort(canonical);
		DataBlock[] canonicalRep = new DataBlock[ALPHABET_SIZE];
		// Holds the current representation.
		int cur = 0;
		// Holds the previous bit length.
		int prevLen = 0;
		for (CanonicalPair p : canonical) {
			// If the bit length has increased, left shift cur. In terms of the
			// huffman tree, this is equivalent to following the left/zero child until
			// we reach a leaf node.
			if (p.length != prevLen) {
				cur <<= (p.length - prevLen);
				prevLen = p.length;
			}
			canonicalRep[p.symbol] = new DataBlock(cur, p.length);
			// Find the next node (potentially we need to descend the tree some more
			// -- see above).
			cur++;
		}
		return canonicalRep;
	}

	/**
	 * Pair storing symbol and bit length for use in generating the canonical
	 * representation of a Huffman tree.
	 */
	private static class CanonicalPair implements Comparable<CanonicalPair> {
		public int symbol;
		public int length;

		public CanonicalPair(int symbol, int length) {
			this.symbol = symbol;
			this.length = length;
		}

		@Override
		public int compareTo(CanonicalPair p) {
			if (length != p.length) {
				return length - p.length;
			}
			if (symbol != p.symbol) {
				return symbol - p.symbol;
			}
			return 0;
		}

	}

	/** Decoder state */
	private enum DecoderState {
		DECODING_BLOCK, DECODING_HEADER, READING_HEADER_INFO, STREAM_END
	}

	/**
	 * Class that, given a Huffman tree and a representation, performs compression
	 * or decompression.
	 */
	private static class HuffmanCoder {
		/** Map from symbol to representation. Used in compression. */
		private final DataBlock[] rep;
		/** Huffman tree. Used in decompression. */
		private final HuffmanNode tree;
		/**
		 * Decoder state. This is necessary in case we cannot extract a full symbol
		 * in one call
		 */
		private HuffmanNode curNode;

		/**
		 * Initialise a HuffmanCoder with tree and rep. These may be null, however,
		 * tree must not be null for decompression, and rep must not be null for
		 * compression.
		 * 
		 * @param tree Huffman tree
		 * @param rep symbol to representation map
		 */
		public HuffmanCoder(HuffmanNode tree, DataBlock[] rep) {
			this.tree = tree;
			this.curNode = tree;
			this.rep = rep;
		}

		/**
		 * Compresses length bytes in data and writes them to out. There may be bits
		 * which could not be put in a full byte leftover in buf.
		 * 
		 * @param data data to compress
		 * @param length number of bytes from data to compress
		 * @param buf bit buffer to write to
		 * @param out output stream to write to
		 * @throws IOException
		 */
		public void compressAndWrite(int[] data, int length, DataBlock buf,
		    OutputStream out) throws IOException {
			for (int i = 0; i < length; ++i) {
				buf.appendAndFlush(rep[data[i]], out);
			}
		}

		/**
		 * Try to decompress a symbol from data. If there is no complete symbol,
		 * return -1. Note that bits will still be consumed from data in this case,
		 * but you will not need to re-provide these bits.
		 * 
		 * @param data bit buffer to read from
		 * @return -1 if no symbol, otherwise the symbol
		 */
		public int decompress(DataBlock data) {
			while (data.length > 0) {
				long bit = data.pullRight(1).data;
				if (curNode.zero != null) {
					curNode = bit == 0 ? curNode.zero : curNode.one;
				}
				if (curNode.zero == null) {
					int c = curNode.c;
					curNode = tree;
					return c;
				}
			}
			return -1;
		}
	}
}
