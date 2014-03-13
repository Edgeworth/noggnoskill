
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import CITS2200.Compressor;

/**
 * Main class which does the compressing. Performs a Burrows Wheeler transform,
 * move to front transform, and Huffman coding.
 * 
 * @author E
 */
public class NoGGNoSkill implements Compressor {
	/** We process the file in blocks of this size. */
	private static final int BLOCK_SIZE = 20 * 1024 * 1024;

	public static void main(String[] args) throws FileNotFoundException {
		if (args.length != 3) {
			System.err.printf("Usage: -[d|c] input_file output_file\n");
			System.exit(1);
		}

		FileInputStream input = new FileInputStream(args[1]);
		FileOutputStream output = new FileOutputStream(args[2]);
		NoGGNoSkill noGGNoSkill = new NoGGNoSkill();
		if (args[0].equals("-c")) {
			noGGNoSkill.compress(input, output);
		} else if (args[0].equals("-d")) {
			noGGNoSkill.decompress(input, output);
		}
	}

	/**
	 * Given an input stream, compress it, and write it to the output stream.
	 * 
	 * @param inputStream
	 * @param outputStream
	 * @return null or exception message if an exception occurred
	 */
	@Override
	public String compress(InputStream inputStream, OutputStream outputStream) {
		InputStream in = new BufferedInputStream(inputStream);
		OutputStream out = new BufferedOutputStream(outputStream);

		// Modular compression via set of 'filters'.
		ZeroComponent compensator = new ZeroComponent();
		BWTComponent bwt = new BWTComponent();
		MTFComponent mtf = new MTFComponent();
		HuffmanComponent huffman = new HuffmanComponent();

		try {
			int b = 0;
			int[] block = new int[BLOCK_SIZE];
			int blockSize = 0;
			List<Integer> buf = new LinkedList<Integer>();

			while (b != -1) {
				b = in.read();
				if (b != -1) {
					block[blockSize++] = b;
				}

				// Once we've read in a block, we want to read it and append it to our
				// buffer. Since it may expand to a different size, we need this extra
				// buffer so we can make sure to feed our Burrows Wheeler with things of
				// exactly BLOCK_SIZE (or less for the last block).
				if (blockSize == BLOCK_SIZE || b == -1) {
					compensator.transformAndAppend(block, blockSize, buf);
					blockSize = 0;
				}

				while (buf.size() > 0) {
					// We're done if we have no more full blocks left and it's not EOF.
					if (buf.size() < BLOCK_SIZE && b != -1) {
						break;
					}

					int[] intermediary = new int[Math.min(BLOCK_SIZE - 1, buf.size()) + 1];
					for (int i = 0; i < intermediary.length - 1; ++i) {
						intermediary[i] = buf.remove(0);
					}
					// We need to add 0 as the EOS marker for BWT to work.
					intermediary[intermediary.length - 1] = 0;
					intermediary = bwt.transform(intermediary, intermediary.length);
					intermediary = mtf.transform(intermediary, intermediary.length);
					huffman.compressAndWrite(intermediary, intermediary.length, (b == -1)
					    && buf.size() == 0, out);
				}
			}
			out.close();
		} catch (IOException e) {
			return e.toString();
		}
		return null;
	}

	/**
	 * Takes an input stream containing data compressed by our program, and
	 * decompresses it to out.
	 * 
	 * @param inputStream
	 * @param outputStream
	 * @return null or exception message if an exception occurred
	 */
	@Override
	public String decompress(InputStream inputStream, OutputStream outputStream) {
		InputStream in = new BufferedInputStream(inputStream);
		OutputStream out = new BufferedOutputStream(outputStream);

		// The structure of this function is very similar to that of compress.
		ZeroComponent compensator = new ZeroComponent();
		BWTComponent bwt = new BWTComponent();
		MTFComponent mtf = new MTFComponent();
		HuffmanComponent huffman = new HuffmanComponent();

		try {
			int b = 0;
			int[] block = new int[BLOCK_SIZE];
			int blockSize = 0;
			List<Integer> buf = new LinkedList<Integer>();

			while (b != -1) {
				b = in.read();
				if (b != -1) {
					block[blockSize++] = b;
				}
				if (blockSize == BLOCK_SIZE || b == -1) {
					huffman.decompressAndAppend(block, blockSize, buf);
					blockSize = 0;
				}

				while (buf.size() > 0) {
					if (buf.size() < BLOCK_SIZE && b != -1) {
						break;
					}

					int[] intermediary = new int[Math.min(BLOCK_SIZE, buf.size())];
					for (int i = 0; i < intermediary.length; ++i) {
						intermediary[i] = buf.remove(0);
					}
					intermediary = mtf
					    .inverseTransform(intermediary, intermediary.length);
					intermediary = bwt
					    .inverseTransform(intermediary, intermediary.length);
					intermediary = compensator.inverseTransform(intermediary,
					    intermediary.length - 1);
					for (int i = 0; i < intermediary.length; ++i) {
						out.write(intermediary[i]);
					}
				}
			}
			out.close();
		} catch (IOException e) {
			return e.toString();
		}
		return null;
	}
}
