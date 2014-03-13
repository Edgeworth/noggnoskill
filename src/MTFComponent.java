
/**
 * Component that performs a move to front forward and inverse transform.
 * Symbols that occur more often locally are assigned lower values using this
 * transform.
 * 
 * @author E
 */
public class MTFComponent {
	/** The current mtf table */
	private final int[] mtf;

	/**
	 * Initialise a new MTFComponent with the default table.
	 */
	public MTFComponent() {
		// This is a table generated from empirical data to optimise for the most
		// common values we get.
		this.mtf = new int[] { 32, 101, 116, 97, 111, 110, 104, 105, 115, 114, 100,
		    108, 13, 10, 117, 109, 99, 44, 102, 119, 121, 103, 112, 98, 46, 118,
		    73, 107, 65, 58, 84, 83, 69, 49, 39, 79, 59, 82, 78, 76, 77, 67, 45,
		    34, 68, 72, 66, 50, 57, 80, 87, 71, 48, 70, 51, 95, 85, 63, 52, 120,
		    41, 40, 53, 33, 74, 89, 54, 106, 56, 55, 113, 122, 75, 37, 86, 91, 93,
		    36, 90, 47, 81, 64, 88, 42, 226, 128, 60, 62, 148, 156, 157, 61, 153,
		    94, 195, 96, 124, 169, 35, 38, 168, 18, 0, 187, 239, 191, 160, 43, 161,
		    170, 194, 163, 177, 125, 188, 126, 180, 162, 123, 152, 137, 178, 174,
		    167, 150, 147, 135, 130, 92, 9, 255, 254, 253, 252, 251, 250, 249, 248,
		    247, 246, 245, 244, 243, 242, 241, 240, 238, 237, 236, 235, 234, 233,
		    232, 231, 230, 229, 228, 227, 225, 224, 223, 222, 221, 220, 219, 218,
		    217, 216, 215, 214, 213, 212, 211, 210, 209, 208, 207, 206, 205, 204,
		    203, 202, 201, 200, 199, 198, 197, 196, 193, 192, 190, 189, 186, 185,
		    184, 183, 182, 181, 179, 176, 175, 173, 172, 171, 166, 165, 164, 159,
		    158, 155, 154, 151, 149, 146, 145, 144, 143, 142, 141, 140, 139, 138,
		    136, 134, 133, 132, 131, 129, 127, 31, 30, 29, 28, 27, 26, 25, 24, 23,
		    22, 21, 20, 19, 17, 16, 15, 14, 12, 11, 8, 7, 6, 5, 4, 3, 2, 1 };
	}

	/**
	 * Reverses MTF transformed data.
	 * 
	 * @param data data to inverse transform
	 * @param length number of elements of data to inverse transform
	 * @return inverse transformed data
	 */
	public int[] inverseTransform(int[] data, int length) {
		int[] output = new int[length];
		for (int i = 0; i < length; ++i) {
			// Output the symbol at the given index.
			int b = data[i];
			output[i] = mtf[b];

			// Move that symbol to the front.
			int val = mtf[b];
			for (; b > 0; --b) {
				mtf[b] = mtf[b - 1];
			}
			mtf[0] = val;
		}
		return output;
	}

	/**
	 * Perform the forward transform on the first length elements of data, and
	 * write the result to buf.
	 * 
	 * @param data data to transform
	 * @param length number of elements of data to transform
	 * @param buf list to write transformed data to
	 */
	public int[] transform(int[] data, int length) {
		int[] output = new int[length];
		for (int i = 0; i < length; ++i) {
			// Find the index that the current symbol is at in the mtf array.
			int idx = 0;
			for (idx = 0; mtf[idx] != data[i]; ++idx) {
				;
			}

			// Output that symbol and move the symbol to the front in the MTF array.
			output[i] = idx;
			int val = mtf[idx];
			for (; idx > 0; --idx) {
				mtf[idx] = mtf[idx - 1];
			}
			mtf[0] = val;
		}
		return output;
	}
}
