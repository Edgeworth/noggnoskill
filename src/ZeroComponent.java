
import java.util.List;

/**
 * Component to transform and inverse transform data to have no zeroes. This is
 * done by swapping 0's with 247's, on the basis that 0's will be more common
 * than 247's. Then, 247's are replaced with two bytes 246 102 and 246 is
 * replaced by 246 101.
 * 
 * @author E
 */
public class ZeroComponent {
	/**
	 * If we read a 246 and there was no more data, we need to store this so we
	 * know to treat the next byte we get specially.
	 */
	boolean stateUnfinished;

	/**
	 * Undoes the forward transform.
	 * 
	 * @param data data to inverse transform
	 * @param length number of elements of data to inverse transform
	 * @return inverse transformed data
	 */
	public int[] inverseTransform(int[] data, int length) {
		// Compute the length of what we're going to end up returning.
		int total = 0;
		for (int i = 0; i < length; ++i) {
			total++;
			// If we see a 246, we only write 1 byte for it and the byte after it.
			if (data[i] == 246) {
				i++;
			}
		}

		// If we're going to get trolled, we won't be able to write out the byte, so
		// decrease our total.
		if (data[length - 1] == 246) {
			total--;
		}

		int[] transformed = new int[total];
		int dataIdx = 0;
		for (int i = 0; i < transformed.length; ++i) {
			transformed[i] = data[dataIdx++];
			// 247's are actually zeros.
			if (transformed[i] == 247) {
				transformed[i] = 0;
			}
			if (stateUnfinished) {
				transformed[i] = transformed[i] + 145;
				stateUnfinished = false;
			} else if (transformed[i] == 246) {
				// 101+145 = 246, 102 + 145 = 247.
				transformed[i] = data[dataIdx++] + 145;
			}
		}

		if (data[length - 1] == 246) {
			stateUnfinished = true;
		}

		return transformed;
	}

	/**
	 * Perform the forward transform on the first length elements of data, and
	 * write the result to buf.
	 * 
	 * @param data data to transform
	 * @param length number of elements of data to transform
	 * @param buf list to write transformed data to
	 */
	public void transformAndAppend(int[] data, int length, List<Integer> buf) {
		for (int i = 0; i < length; ++i) {
			// 0's are actually 247's.
			if (data[i] == 0) {
				buf.add(247);
			} else if (data[i] == 246) { // 246's are actually 246 101's
				buf.add(246);
				buf.add(101);
			} else if (data[i] == 247) { // 247's are actually 246 102's
				buf.add(246);
				buf.add(102);
			} else { // No tricks here.
				buf.add(data[i]);
			}
		}
	}
}
