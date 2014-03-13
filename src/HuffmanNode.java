import java.util.PriorityQueue;

/**
 * Node in a Huffman tree. Also contains static utility methods for building
 * Huffman trees.
 * 
 * @author E
 */
public class HuffmanNode implements Comparable<HuffmanNode> {
	/**
	 * Creates a map from symbols in a Huffman tree to representations. If a
	 * symbol is not represented, the associated DataBlock will be null. The
	 * returned array will be of size alphabetSize, so it must be ensured that all
	 * symbols are less than alphabetSize.
	 * 
	 * @param root root of a Huffman tree
	 * @param alphabetSize size of the alphabet
	 * @return map from symbol to representation
	 */
	public static DataBlock[] generateRep(HuffmanNode root, int alphabetSize) {
		DataBlock[] rep = new DataBlock[alphabetSize];
		generateRepInternal(root, rep, new DataBlock());
		return rep;
	}

	/**
	 * Creates a Huffman tree from a map from symbols to representations.
	 * 
	 * @param rep representation map
	 * @return huffman tree root
	 */
	public static HuffmanNode generateTree(DataBlock[] rep) {
		HuffmanNode root = new HuffmanNode();
		for (int i = 0; i < rep.length; ++i) {
			if (rep[i] != null) {
				DataBlock block = rep[i].copy();
				HuffmanNode cur = root;
				// If we've reached what should be a leaf node, set its symbol and
				// return.
				while (block.length > 0) {
					long bit = block.pullRight(1).data;

					// Create the children if necessary.
					if (bit == 0) {
						if (cur.zero == null) {
							cur.zero = new HuffmanNode();
						}
						cur = cur.zero;
					} else {
						if (cur.one == null) {
							cur.one = new HuffmanNode();
						}
						cur = cur.one;
					}
				}
				cur.c = i;
			}
		}
		return root;
	}

	/**
	 * Generates a Huffman tree from a frequency table. Any frequencies that are 0
	 * will not have their associated symbol included in the Huffman tree.
	 * 
	 * @param table array of frequencies
	 * @return root of a Huffman tree
	 */
	public static HuffmanNode generateTree(int[] table) {
		PriorityQueue<HuffmanNode> pq = new PriorityQueue<HuffmanNode>();
		for (int i = 0; i < table.length; ++i) {
			if (table[i] != 0) {
				pq.add(new HuffmanNode(table[i], i));
			}
		}

		// We repeatedly merge the subtrees with the least frequency, so symbols
		// with smaller frequencies will be further down the tree.
		while (!pq.isEmpty()) {
			HuffmanNode a = pq.remove();
			if (pq.isEmpty()) {
				return a;
			}
			HuffmanNode b = pq.remove();
			pq.add(new HuffmanNode(a.freq + b.freq, a, b));
		}
		return null;
	}

	/**
	 * Utility function used in generateRep; does the actual work.
	 * 
	 * @param root Huffman subtree
	 * @param rep map from symbols to representations.
	 * @param cur current representation we have traversed
	 */
	private static void generateRepInternal(HuffmanNode root, DataBlock[] rep,
	    DataBlock cur) {
		// If we're at a leaf node, set its representation.
		if (root.zero == null) {
			rep[root.c] = cur.copy();
		} else { // Otherwise traverse both children.
			DataBlock zero = cur.copy();
			zero.pushLeft(0, 1);
			generateRepInternal(root.zero, rep, zero);

			DataBlock one = cur.copy();
			one.pushLeft(1, 1);
			generateRepInternal(root.one, rep, one);
		}
	}

	/** The frequency of this symbol or subtree */
	public int freq;
	/** The symbol. It is -1 if we aren't a leaf node */
	public int c;
	/** Our children. It is null if we are a leaf node */
	public HuffmanNode zero;
	public HuffmanNode one;

	/**
	 * Create a leaf node with an invalid symbol.
	 */
	public HuffmanNode() {
		this.freq = 0;
		this.c = -1;
		this.zero = null;
		this.one = null;
	}

	/**
	 * Create an internal node with cumulative frequency freq and children zero
	 * and one.
	 * 
	 * @param freq cumulative frequency
	 * @param zero zero child
	 * @param one one child
	 */
	public HuffmanNode(int freq, HuffmanNode zero, HuffmanNode one) {
		this.freq = freq;
		this.c = -1;
		this.zero = zero;
		this.one = one;
	}

	/**
	 * Create a leaf node with frequency freq and symbol c
	 * 
	 * @param freq frequency
	 * @param c symbol
	 */
	public HuffmanNode(int freq, int c) {
		this.freq = freq;
		this.c = c;
		this.zero = null;
		this.one = null;
	}

	/**
	 * Order by lowest frequency. Used so we can put it into a priority queue when
	 * building a tree.
	 * 
	 * @param n node to compare
	 * @return 0 if same frequency, otherwise negative if our frequency is lower,
	 *         otherwise positive.
	 */
	@Override
	public int compareTo(HuffmanNode n) {
		return freq - n.freq;
	}
}
