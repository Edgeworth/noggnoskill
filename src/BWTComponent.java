import java.util.Arrays;

/**
 * Component that performs a Burrows Wheeler forward and inverse transform in
 * linear time.
 * 
 * @author E
 */
public class BWTComponent {
	/**
	 * Compute the inverse BWT transform of the first size elements of input.
	 * 
	 * @param input data to inverse transform
	 * @param size number of elements of data to inverse transform
	 * @return inverse transformed data
	 */
	public int[] inverseTransform(int[] input, int size) {
		// This represents the number of characters lexicographically smaller than
		// the indexed character in the input.
		int[] numCharactersBefore = new int[256];
		// numSameBefore[i] represents the number of characters equal to input[i] in
		// input[0:i] (not including input[i]).
		int[] numSameBefore = new int[size];

		// Cur represents the index of the row which ends in the EOS character.
		// Initially it should be set to the index of the EOS character.
		int cur = 0;
		for (int i = 0; i < size; ++i) {
			if (input[i] == 0) {
				cur = i;
			}
			numSameBefore[i] = numCharactersBefore[input[i]];
			// At the moment numCharactersBefore is just a frequency table.
			numCharactersBefore[input[i]]++;
		}
		int sum = 0;
		// Now we accumulate over it and turn it into what it's meant to be.
		for (int i = 0; i < 256; ++i) {
			sum += numCharactersBefore[i];
			numCharactersBefore[i] = sum - numCharactersBefore[i];
		}

		// To inverse a burrows wheeler transform, we continually 'place' the input
		// string in a column before our current rows of characters, and then sort
		// the rows. The row which ends in EOS is our original string.
		int[] output = new int[size];
		for (int i = 0; i < size; ++i) {
			output[size - i - 1] = input[cur];
			// We're essentially partitioning over and over again. We know that all
			// rows starting in characters which are lexicographically less than ours
			// are definitely going earlier than us. Ones starting with the same
			// character will go before us if they're already before us, since
			// inductively our current rows (although they don't exist explicitly) are
			// already sorted.
			cur = numCharactersBefore[input[cur]] + numSameBefore[cur];
		}
		return output;
	}

	/**
	 * Compute the BWT transform of the first size elements of input. There must
	 * be no zeroes in input except for one at input[size-1].
	 * 
	 * @param input data to transform
	 * @param size number of elements of data to transform
	 * @return transformed data
	 */
	public int[] transform(int[] input, int size) {
		int[] suffixes = sais(input, size);
		int[] output = new int[size];
		for (int i = 0; i < suffixes.length; ++i) {
			// We are guaranteed that the suffixes will be in the same order sorted as
			// the rotations of the string, since the string is terminated by a unique
			// lexicographically smallest character. So we just, for each suffix, find
			// the character in the string that would be at the end to compute the
			// bwt.
			output[i] = input[(suffixes[i] - 1 + size) % size];
		}
		return output;
	}

	/**
	 * Returns the induced sorting of all suffixes of s given the elements in lms
	 * are in sorted order. We also use this function to sort all the lms prefixes
	 * if the elements of lms are not in sorted order.
	 * 
	 * @param bucketStart start of the L type buckets
	 * @param bucketEnd end of the S type buckets
	 * @param s string to get suffixes/lms prefixes from
	 * @param sSize size of s
	 * @param t types of characters/suffixes in s
	 * @param lms sorted lms suffixes
	 * @param lmsLength number of elements in lms
	 * @return induce sorted array
	 */
	private int[] induce(int[] bucketStart, int[] bucketEnd, int[] s, int sSize,
	    boolean[] t, int[] lms, int lmsLength) {
		// Initialise our array to say everything hasn't been set so far.
		int[] induced = new int[sSize];
		Arrays.fill(induced, -1);

		// Place the initial elements from lms known to be sorted in the end of
		// the
		// S-type buckets, preserving the order (hence traversing lms backwards).
		int[] bucketEndCopy = new int[bucketEnd.length];
		System.arraycopy(bucketEnd, 0, bucketEndCopy, 0, bucketEnd.length);
		for (int i = lmsLength - 1; i >= 0; --i) {
			induced[bucketEndCopy[s[lms[i]]]--] = lms[i];
		}

		// Use the sorted lms elements to sort the L type strings via induced
		// sorting.
		int[] bucketStartCopy = new int[bucketStart.length];
		System.arraycopy(bucketStart, 0, bucketStartCopy, 0, bucketStart.length);
		System.arraycopy(bucketEnd, 0, bucketEndCopy, 0, bucketEnd.length);
		for (int i = 0; i < induced.length; ++i) {
			// If induced_i is has been set, and the string before it is L type,
			// place
			// it in its appropriate bucket.
			if (induced[i] > 0 && !t[induced[i] - 1]) {
				induced[bucketStartCopy[s[induced[i] - 1]]++] = induced[i] - 1;
			}
		}

		// Use the sorted L type strings to sort the S type strings (overwriting
		// the
		// original lms strings placed)
		for (int i = induced.length - 1; i >= 0; --i) {
			// If induced_i is has been set, and the string before it is S type,
			// place
			// it in its appropriate bucket (possibly overwriting).
			if (induced[i] > 0 && t[induced[i] - 1]) {
				induced[bucketEndCopy[s[induced[i] - 1]]--] = induced[i] - 1;
			}
		}

		return induced;
	}

	/**
	 * Compute the suffix array of the first sSize elements of s. Must have 0 at
	 * end of array as sentinel.
	 * 
	 * @param s data to compute the suffix array of
	 * @param sSize how much data is in s
	 */
	private int[] sais(int[] s, int sSize) {
		// Base case is our string has nothing in it (except EOS character)
		if (sSize == 1) {
			return new int[] { 0 };
		}

		// Generate S, L classifications, find LMS substring indices, and find max
		// character.
		// t[i]: true iff suffix S_i is an S type suffix.
		boolean[] t = new boolean[sSize];
		// Guaranteed that the number of lms substrings is < |S|/2
		int[] lms = new int[sSize];
		t[sSize - 1] = true; // EOS must be S type.
		int lmsSize = 0; // Number of LMS substrings.
		// Store the the maximum character to see how big our buckets need to be.
		int maxChar = 0;
		// Look at every character except EOS in s, backwards.
		for (int i = sSize - 2; i >= 0; --i) {
			// If S_i < S_i+1 then it's S type. Otherwise, if it's equal, it's S
			// type
			// if S_i+1 was S type. Otherwise, it's L type.
			t[i] = (s[i] < s[i + 1]) || ((s[i] == s[i + 1]) && t[i + 1]);
			if (s[i] >= maxChar) {
				maxChar = s[i] + 1;
			}

			// If we have an L type then and S type (...LS...), then i+1 is the
			// start
			// of an LMS substring.
			if (!t[i] && t[i + 1]) {
				lms[lmsSize++] = i + 1;
			}
		}

		// Calculate frequency table for use in building further data structures.
		int[] charCount = new int[maxChar];
		for (int i = 0; i < sSize; ++i) {
			charCount[s[i]]++;
		}

		// We divide the suffix array up into buckets based on first character.
		// We further divide those up into L-type buckets and S-type buckets, in
		// that order. We record the start of the L-type buckets in bucketStart.
		// We record the end of the S-type buckets in bucketEnd.
		int[] bucketStart = new int[maxChar];
		int[] bucketEnd = new int[maxChar];
		int sum = 0;
		for (int i = 0; i < maxChar; ++i) {
			bucketStart[i] = sum;
			sum += charCount[i];
			bucketEnd[i] = sum - 1;
		}

		// We use induced sorting to sort the LMS substrings.
		int[] inducedSorted = induce(bucketStart, bucketEnd, s, sSize, t, lms,
		    lmsSize);
		// We record, for each LMS substring, its index based on its position in
		// the sorted array, with duplicates removed.
		int[] lmsMap = new int[sSize];
		int curPartition = 0;
		int prev = -1;
		for (int i = 0; i < inducedSorted.length; ++i) {
			int cur = inducedSorted[i];
			// We're looking for our LMS substrings, which must be S type
			// (so they cannot start at the 0th index).
			if (cur > 0 && t[cur] && !t[cur - 1]) {
				// Since they're sorted in inducedSorted, any duplicates will appear
				// next to each other. We just check if it was the same as its
				// previous.
				if (prev != -1) {
					// We use these to keep track of when the LMS substring finishes.
					boolean prevPastS = false;
					boolean curPastS = false;
					for (int j = 0; true; ++j) {
						// If we see an L, the next S we see will be the end of prev's LMS
						// substring.
						if (!t[prev + j]) {
							prevPastS = true;
						}

						// Similar.
						if (!t[cur + j]) {
							curPastS = true;
						}

						// We define two LMS substrings to be the same if they are the
						// same
						// length, have the same characters, and the same types.
						if (s[prev + j] != s[cur + j] || t[prev + j] != t[cur + j]) {
							// If they weren't equal, increment the index for our bucketing
							// of
							// LMS substrings.
							curPartition++;
							break;
						}

						// If we see two S's, and they end both the strings, then the
						// strings must have been the same.
						if (t[prev + j] && t[cur + j] && prevPastS && curPastS) {
							break;
						}
					}
				}
				lmsMap[cur] = curPartition;
				prev = cur;
			}
		}

		// Create our new string S1 which will be at most |S|/2 in length.
		int[] s1 = new int[lmsSize];
		for (int i = 0; i < lmsSize; ++i) {
			s1[i] = lmsMap[lms[lmsSize - i - 1]];
		}

		// Recursively solve.
		int[] lmsSA = sais(s1, s1.length);
		// Create new array with the original indices (we bucketed them to
		// preserve
		// sorting properties).
		int[] lmsSAIndices = new int[lmsSA.length];
		for (int i = 0; i < lmsSA.length; ++i) {
			lmsSAIndices[i] = lms[lmsSize - lmsSA[i] - 1];
		}
		// We get the sorted LMS suffixes and use them to induce a sorting of the
		// rest of the suffixes.
		return induce(bucketStart, bucketEnd, s, sSize, t, lmsSAIndices,
		    lmsSAIndices.length);
	}
}
