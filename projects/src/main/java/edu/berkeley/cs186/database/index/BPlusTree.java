package edu.berkeley.cs186.database.index;

import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.io.PageAllocator;
import edu.berkeley.cs186.database.datatypes.DataType;
import edu.berkeley.cs186.database.table.RecordID;
import edu.berkeley.cs186.database.datatypes.*;

import java.util.NoSuchElementException;
import java.util.Iterator;
import java.util.List;
import java.nio.file.Paths;

/**
 * A B+ tree. Allows the user to add, delete, search, and scan for keys in an
 * index. A B+ tree has an associated page allocator. The first page in the page
 * allocator is a header page that serializes the search key data type, root
 * node page, and first leaf node page. Each subsequent page is a BPlusNode,
 * specifically either an InnerNode or LeafNode.
 *
 * Properties:
 * `allocator`: the PageAllocator for this index
 * `keySchema`: the DataType for this index's search key
 * `rootPageNum`: page number of the root node of this tree
 * `firstLeafPageNum`: page number of the first leaf node of this tree
 */
public class BPlusTree {
  public static final String FILENAME_PREFIX = "db";
  public static final String FILENAME_EXTENSION = ".index";

  protected PageAllocator allocator;
  protected DataType keySchema; 
  private int rootPageNum;
  private int firstLeafPageNum;

  /**
   * This constructor is used for creating an empty BPlusTree.
   *
   * @param keySchema the schema of the index key
   * @param fName the filename of where the index will be built
   */

  public BPlusTree(DataType keySchema, String fName) {
    this(keySchema, fName, FILENAME_PREFIX); 
  }

  public BPlusTree(DataType keySchema, String fName, String filePrefix) {
    String pathname = Paths.get(filePrefix, fName + FILENAME_EXTENSION).toString();
    this.allocator = new PageAllocator(pathname, true);
    this.keySchema = keySchema;
    int headerPageNum = this.allocator.allocPage();
    assert(headerPageNum == 0);
    BPlusNode root = new LeafNode(this);  
    this.rootPageNum = root.getPageNum();
    this.firstLeafPageNum = rootPageNum;
    //The first page stores information about this index file.
    writeHeader();
  }

  /**
   * This constructor is used for loading a BPlusTree from a file.
   *
   * @param fName the filename of a preexisting BPlusTree
   */

  public BPlusTree(String fName) {
    this(fName, FILENAME_PREFIX);
  }

  public BPlusTree(String fName, String filePrefix) {
    String pathname = Paths.get(filePrefix, fName + FILENAME_EXTENSION).toString();
    this.allocator = new PageAllocator(pathname, false);
    this.readHeader();
  }

  /**
   * Performs a sorted scan from the beginning of the index to the end.
   *
   * @return Iterator of all RecordIDs in sorted order.
   */

  public Iterator<RecordID> sortedScan() {
    LeafNode firstLeaf = new LeafNode(this, firstLeafPageNum);
    return new BPlusIterator(firstLeaf); 
  }

  /**
   * Performs a sorted scan starting from some value.
   *
   * @param keyStart the value from where to start the iterator.
   * @return Iterator of RecordIDs that are equal to or greater than keyStart in sorted order.
   */

  public Iterator<RecordID> sortedScanFrom(DataType keyStart) {
    BPlusNode root = BPlusNode.getBPlusNode(this, rootPageNum);
    LeafNode leaf = root.locateLeaf(keyStart, true);
  
    return new BPlusIterator(leaf, keyStart, true); 
  }

  /**
   * Performs a lookup in the index that matches an exact key.
   *
   * @param key the value of the exact key match.
   * @return Iterator of RecordIDs that match an exact key. 
   */

  public Iterator<RecordID> lookupKey(DataType key) {
    BPlusNode root = BPlusNode.getBPlusNode(this, rootPageNum);
    LeafNode leaf = root.locateLeaf(key, true);
    return new BPlusIterator(leaf, key, false); 
  }

  /**
   * Inserts a (Key, RecordID) tuple into the BPlusTree index.
   *
   * @param key the key to insert
   * @param rid the RecordID of where the key is located in the table.
   */

  public void insertKey(DataType key, RecordID rid) {
    BPlusNode.getBPlusNode(this, rootPageNum).insertKey(key, rid); 
  }
  
  /**
   * Deletes an entry with the matching Key and RecordID
   *
   * @param key the key to be deleted.
   * @param rid the RecordID of the key to be deleted
   */

  public boolean deleteKey(DataType key, RecordID rid) {
    /*You will not have to implement this in this project*/
    throw new BPlusTreeException("BPlusTree#DeleteKey Not Implemented!");
  }

  /**
   * Performs a lookup to see if index contains a given key
   *
   * @param key the key to be checked if exists in index
   * @return a boolean if key exists in index.
   */

  public boolean containsKey(DataType key) {
    return lookupKey(key).hasNext();
  }
 
  /**
   * Updates where the root page is. Should be called whenever the root node has been split
   *
   * @param pNum the page number of where the new root now is
   */

  protected void updateRoot(int pNum) {
    this.rootPageNum = pNum;
    writeHeader();
  }


  private void writeHeader() {
    Page headerPage = allocator.fetchPage(0);
    int bytesWritten = 0;
    
    headerPage.writeInt(bytesWritten, this.rootPageNum);
    bytesWritten += 4;
   
    headerPage.writeInt(bytesWritten, this.firstLeafPageNum);
    bytesWritten += 4;

    headerPage.writeInt(bytesWritten, keySchema.type().ordinal());
    bytesWritten += 4;
    
    if (this.keySchema.type().equals(DataType.Types.STRING)) {
      headerPage.writeInt(bytesWritten, this.keySchema.getSize());
      bytesWritten += 4;
    }
    headerPage.flush();
  }

  private void readHeader() {
    Page headerPage = allocator.fetchPage(0);
    
    int bytesRead = 0;
    
    this.rootPageNum = headerPage.readInt(bytesRead);
    bytesRead += 4;
   
    this.firstLeafPageNum = headerPage.readInt(bytesRead);
    bytesRead += 4;

    int keyOrd = headerPage.readInt(bytesRead);
    bytesRead += 4;
    DataType.Types type = DataType.Types.values()[keyOrd];
    
    switch(type) {
    case INT:
      this.keySchema = new IntDataType();
      break;
    case STRING:
      int len = headerPage.readInt(bytesRead);
      bytesRead += 4;
      this.keySchema = new StringDataType(len);
      break;
    case BOOL:
      this.keySchema = new BoolDataType();
      break;
    case FLOAT:
      this.keySchema = new FloatDataType();
      break;
    }
  }
  /**
   * Dumps all of tree leaves.
   */
  public void dumpLeaves(){
        LeafNode leaf = (LeafNode)BPlusNode.getBPlusNode(
                this, this.firstLeafPageNum);
        int index = 0;
        System.out.print("First node: pageNum=" + leaf.getPageNum() + "; ");
        for(BEntry entry: leaf.getAllValidEntries()){
            System.out.print("["+index+"]" + entry.toString()+", ");
            index++;
        }
        System.out.println();

        int inner_node_page = leaf.getParent();
        int inner_counter = 1;
        int inner_node_index = 0;
        System.out.print(inner_counter + "th inner node: pageNum=" + 
                inner_node_page + "; ");
        for(BEntry entry: BPlusNode.getBPlusNode(this, 
                    inner_node_page).getAllValidEntries()){
            System.out.print("[" + inner_node_index + "]" + 
                    entry.toString() + ", ");
            inner_node_index++;
        }
        System.out.println();
        inner_counter++;

        int node_num = 2;
        while(leaf.getNextLeaf() > -1){
            leaf = (LeafNode)BPlusNode.getBPlusNode(
                    this, leaf.getNextLeaf());
            System.out.print(node_num + "th node: pageNum=" + leaf.getPageNum() + "; ");
            for(BEntry entry: leaf.getAllValidEntries()){
                System.out.print("[" + index + "]" + 
                        entry.toString()+", ");
                index++;
            }
            System.out.println();
            node_num++;

            inner_node_index = 0;
            System.out.print(inner_counter + "th inner node: pageNum=" + 
                    inner_node_page + "; ");
            for(BEntry entry: BPlusNode.getBPlusNode(this,
                        inner_node_page).getAllValidEntries()){
                System.out.print("[" + inner_node_index + "]" + 
                        entry.toString() + ", ");
                inner_node_index++;
            }
            System.out.println();
            inner_counter++;
        }
    }
  /**
   * An implementation of Iterator that provides an iterator interface over RecordIDs
   * in this index.
   */
  
  private class BPlusIterator implements Iterator<RecordID> {
    private LeafNode currLeaf; 
    private Iterator<RecordID> currLeafIter;
    private DataType lookupKey = null;
    private boolean isScan;

  /**
   * This constructor creates an Iterator that scans starting from some LeafNode.
   *
   * @param leaf the LeafNode to start scanning from.
   */
  
    public BPlusIterator(LeafNode leaf) {
        this.isScan = true;
        currLeafIter = leaf.scan();
        if(currLeafIter.hasNext()){
            currLeaf = leaf;
        }else{
            while(leaf.getNextLeaf() > -1){
                leaf = (LeafNode) BPlusNode.getBPlusNode(BPlusTree.this, 
                        leaf.getNextLeaf());
                currLeafIter = leaf.scan();
                if(currLeafIter.hasNext()){
                    currLeaf = leaf;
                    return;
                }
            }
            //Nerver found
            currLeafIter = null;
        }
    }
    
   
  /**
   * This constructor creates an Iterator that scans starting from some LeafNode and some starting value.
   * Can be used for LookupKey or sortedScanFrom via a boolean toggle.
   *
   * @param leaf the LeafNode to start scanning from.
   * @param key the key to that has to match
   * @param scan boolean to toggle lookupKey that matches an exact key vs a sortedScanFrom that starts from some value
   */

    public BPlusIterator(LeafNode leaf, DataType key, boolean scan) {
        if(key instanceof IntDataType){
            if(key.compareTo(new IntDataType(204)) == 0){
                System.out.println("Starting dump parent entry of node: " + leaf.getPageNum());
                int index = 0;
                for(BEntry entry: ((InnerNode)(BPlusNode.getBPlusNode(leaf.getTree(), 
                                leaf.getParent()))).getAllValidEntries()){
                    System.out.print("[" + index + "]" + entry + ", ");
                    index ++;
                }
                System.out.println();
            }
        }
        this.isScan = scan;
        this.lookupKey = key;
        if(isScan){
            currLeafIter = leaf.scanFrom(this.lookupKey);
        }else{
            currLeafIter = leaf.scanForKey(this.lookupKey);
        }
        if(currLeafIter.hasNext()){
            currLeaf = leaf;
        }else{
            //System.out.println("Do not have key(" + key + ") on this leaf");
            int index = 0;
            while(leaf.getNextLeaf() > -1){
                index++;
                leaf = (LeafNode)BPlusNode.getBPlusNode(BPlusTree.this, leaf.getNextLeaf());
                if(isScan){
                    currLeafIter = leaf.scanFrom(this.lookupKey);
                }else{
                    currLeafIter = leaf.scanForKey(this.lookupKey);
                    //System.out.println("After tries " + index + 
                    //        " more leaves,  we have it");
                }
                if(currLeafIter.hasNext()){
                    currLeaf = leaf;
                    return;
                }
            }
            //Nerver found
            currLeafIter = null;
        }
    }

    public boolean hasNext() {
        return (currLeafIter != null)?currLeafIter.hasNext():false;
    }
    
    /**
     * Yields the next RecordID of this iterator.
     *
     * @return the next RecordID
     * @throws NoSuchElementException if there are no more Records to yield
     */
    public RecordID next() {
      if(hasNext()){
          RecordID record = currLeafIter.next();
          if(!currLeafIter.hasNext()){
              //System.out.println("Current record: " + record.toString() + 
              //        ". Start trying next leaf");
              currLeafIter = null;
              int count = 0;
              while(currLeaf.getNextLeaf() > -1){
                  count++;
                  currLeaf = (LeafNode) BPlusNode.getBPlusNode(BPlusTree.this, 
                          currLeaf.getNextLeaf());
                  List<BEntry> entries = currLeaf.getAllValidEntries();
                  if(entries.size() > 0){
                      if(!this.isScan){
                          if(entries.get(0).getKey().compareTo(this.lookupKey) == 0){
                              currLeafIter = currLeaf.scanForKey(this.lookupKey);
                              //System.out.println("After tries " + count + 
                              //        " more leaves, we have it");
                          }else{
                              //System.out.println("After tries " + count + 
                              //    " more leaves, we never met the key " + this.lookupKey);
                              //System.out.print("Searching entries: ");
                              int index = 0;
                              for(BEntry entry: entries){
                              //    System.out.print("[" + index + "]" + entry.toString()
                              //            + " ");
                                  index++;
                              }
                              //System.out.println();

                              index = 0;
                              while(currLeaf.getNextLeaf() > -1){
                                  index++;
                                  currLeaf = (LeafNode)BPlusNode.getBPlusNode(
                                              BPlusTree.this, currLeaf.getNextLeaf());
                              }
                             // System.out.println("Currently we are " + index +
                             //     " away from the end");

                              //System.out.println("Now, dump all of leaves: ");
                              //BPlusTree.this.dumpLeaves();
                          }
                      }else{
                          currLeafIter = currLeaf.scan();
                          //System.out.println("After tries " + count + 
                          //        " more leaves, we found a non-empty leaf to scan");
                      }
                      break;
                  }
                  //Bypass empty leaf nodes
              }
              // No more records
              if(currLeafIter == null){
                  //System.out.println("We already on the last leaf");
              }
          }
          return record;
      }
      throw new NoSuchElementException();
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
