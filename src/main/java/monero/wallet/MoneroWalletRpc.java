package monero.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import common.types.Filter;
import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroBlockHeader;
import monero.daemon.model.MoneroKeyImage;
import monero.daemon.model.MoneroOutput;
import monero.daemon.model.MoneroTx;
import monero.rpc.MoneroRpcConnection;
import monero.rpc.MoneroRpcException;
import monero.utils.MoneroException;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroAccountTag;
import monero.wallet.model.MoneroAddressBookEntry;
import monero.wallet.model.MoneroCheckReserve;
import monero.wallet.model.MoneroCheckTx;
import monero.wallet.model.MoneroDestination;
import monero.wallet.model.MoneroIncomingTransfer;
import monero.wallet.model.MoneroIntegratedAddress;
import monero.wallet.model.MoneroKeyImageImportResult;
import monero.wallet.model.MoneroOutgoingTransfer;
import monero.wallet.model.MoneroOutputWallet;
import monero.wallet.model.MoneroSubaddress;
import monero.wallet.model.MoneroSyncListener;
import monero.wallet.model.MoneroSyncResult;
import monero.wallet.model.MoneroTransfer;
import monero.wallet.model.MoneroTxWallet;
import monero.wallet.request.MoneroOutputRequest;
import monero.wallet.request.MoneroSendRequest;
import monero.wallet.request.MoneroTransferRequest;
import monero.wallet.request.MoneroTxRequest;

/**
 * Implements a Monero Wallet using monero-wallet-rpc.
 */
public class MoneroWalletRpc extends MoneroWalletDefault {

  private MoneroRpcConnection rpc;  // handles rpc interactions
  private Map<Integer, Map<Integer, String>> addressCache;  // cache static addresses to reduce requests
  
  // logger
  private static final Logger LOGGER = Logger.getLogger(MoneroWalletRpc.class);
  
  public MoneroWalletRpc(URI uri) {
    this(new MoneroRpcConnection(uri));
  }
  
  public MoneroWalletRpc(String uri) {
    this(new MoneroRpcConnection(uri));
  }
  
  public MoneroWalletRpc(String uri, String username, String password) {
    this(new MoneroRpcConnection(uri, username, password));
  }
  
  public MoneroWalletRpc(URI uri, String username, String password) {
    this(new MoneroRpcConnection(uri, username, password));
  }
  
  public MoneroWalletRpc(MoneroRpcConnection rpc) {
    this.rpc = rpc;
    addressCache = new HashMap<Integer, Map<Integer, String>>();
  }
  
  // --------------------------- RPC WALLET METHODS ---------------------------
  
  /**
   * Get the wallet's RPC connection.
   * 
   * @return the wallet's rpc connection
   */
  public MoneroRpcConnection getRpcConnection() {
    return rpc;
  }

  /**
   * Rescan the blockchain for spent outputs.
   */
  public void rescanSpent() {
    rpc.sendJsonRequest("rescan_spent");
  }
  
  public void rescanBlockchain() {
    rpc.sendJsonRequest("rescan_blockchain");
  }
  
  /**
   * Create a new wallet file at the remote endpoint.
   * 
   * @param filename is the name of the wallet file to create
   * @param password is the password to decrypt the wallet file
   * @param language is the language for the wallet's mnemonic seed
   */
  public void createWallet(String filename, String password, String language) {
    if (filename == null || filename.isEmpty()) throw new MoneroException("Filename is not initialized");
    if (password == null || password.isEmpty()) throw new MoneroException("Password is not initialized");
    if (language == null || language.isEmpty()) throw new MoneroException("Language is not initialized");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("filename", filename);
    params.put("password", password);
    params.put("language", language);
    rpc.sendJsonRequest("create_wallet", params);
  }
  
  /**
   * Open a wallet file at the remote endpoint.
   * 
   * @param filename is the name of the wallet file to open
   * @param password is the password to decrypt the wallet file
   */
  public void openWallet(String filename, String password) {
    if (filename == null || filename.isEmpty()) throw new MoneroException("Filename is not initialized");
    if (password == null || password.isEmpty()) throw new MoneroException("Password is not initialized");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("filename", filename);
    params.put("password", password);
    rpc.sendJsonRequest("open_wallet", params);
    addressCache.clear();
  }
  
  /**
   * Save the currently open wallet file at the remote endpoint.
   */
  public void save() {
    rpc.sendJsonRequest("store");
  }
  
  /**
   * Close the wallet at the remote endpoint, saving the current state.
   */
  public void close() {
    rpc.sendJsonRequest("stop_wallet");
    addressCache.clear();
  }
  
  // -------------------------- COMMON WALLET METHODS -------------------------

  @Override
  public String getSeed() {
    throw new MoneroException("monero-wallet-rpc does not support getting the wallet seed");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getMnemonic() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_type", "mnemonic");
    Map<String, Object> resp = rpc.sendJsonRequest("query_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("key");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getPublicViewKey() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_type", "view_key");
    Map<String, Object> resp = rpc.sendJsonRequest("query_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("key");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getPrivateViewKey() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_type", "view_key");
    Map<String, Object> resp = rpc.sendJsonRequest("query_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("key");
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getLanguages() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_languages");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (List<String>) result.get("languages");
  }

  @SuppressWarnings("unchecked")
  @Override
  public long getHeight() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_height");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return ((BigInteger) result.get("height")).longValue();
  }

  @Override
  public long getChainHeight() {
    throw new MoneroException("monero-wallet-rpc does not support getting the chain height");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroIntegratedAddress getIntegratedAddress(String paymentId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("payment_id", paymentId);
    Map<String, Object> resp = rpc.sendJsonRequest("make_integrated_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    String integratedAddressStr = (String) result.get("integrated_address");
    return decodeIntegratedAddress(integratedAddressStr);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroIntegratedAddress decodeIntegratedAddress(String integratedAddress) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("integrated_address", integratedAddress);
    Map<String, Object> resp = rpc.sendJsonRequest("split_integrated_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return new MoneroIntegratedAddress((String) result.get("standard_address"), (String) result.get("payment_id"), integratedAddress);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroSyncResult sync(Long startHeight, Long endHeight, MoneroSyncListener listener) {
    if (endHeight != null) throw new MoneroException("Monero Wallet RPC does not support syncing to an end height");
    if (listener != null) throw new MoneroException("Monero Wallet RPC does not support reporting sync progress");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("start_height", startHeight);
    Map<String, Object> resp = rpc.sendJsonRequest("refresh", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return new MoneroSyncResult(((BigInteger) result.get("blocks_fetched")).longValue(), (Boolean) result.get("received_money"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean isMultisigImportNeeded() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_balance");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return Boolean.TRUE.equals((Boolean) result.get("multisig_import_needed"));
  }
  
  @Override
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses, String tag) {
    return getAccounts(includeSubaddresses, tag, false);
  }

  @SuppressWarnings("unchecked")
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses, String tag, boolean skipBalances) {
    
    // fetch accounts from rpc
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tag", tag);
    Map<String, Object> resp = rpc.sendJsonRequest("get_accounts", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // build account objects and fetch subaddresses per account using get_address
    // TODO monero-wallet-rpc: get_address should support all_accounts so not called once per account
    List<MoneroAccount> accounts = new ArrayList<MoneroAccount>();
    for (Map<String, Object> rpcAccount : (List<Map<String, Object>>) result.get("subaddress_accounts")) {
      MoneroAccount account = convertRpcAccount(rpcAccount);
      if (includeSubaddresses) account.setSubaddresses(getSubaddresses(account.getIndex(), null, true));
      accounts.add(account);
    }
    
    // fetch and merge fields from get_balance across all accounts
    if (includeSubaddresses && !skipBalances) {
      
      // these fields are not initialized if subaddress is unused and therefore not returned from `get_balance`
      for (MoneroAccount account : accounts) {
        for (MoneroSubaddress subaddress : account.getSubaddresses()) {
          subaddress.setBalance(BigInteger.valueOf(0));
          subaddress.setUnlockedBalance(BigInteger.valueOf(0));
          subaddress.setNumUnspentOutputs(0);
          subaddress.setNumBlocksToUnlock(0);
        }
      }
      
      // fetch and merge info from get_balance
      params.clear();
      params.put("all_accounts", true);
      resp = rpc.sendJsonRequest("get_balance", params);
      result = (Map<String, Object>) resp.get("result");
      if (result.containsKey("per_subaddress")) {
        for (Map<String, Object> rpcSubaddress : (List<Map<String, Object>>) result.get("per_subaddress")) {
          MoneroSubaddress subaddress = convertRpcSubaddress(rpcSubaddress);
          
          // merge info
          MoneroAccount account = accounts.get(subaddress.getAccountIndex());
          assertEquals("RPC accounts are out of order", account.getIndex(), subaddress.getAccountIndex());  // would need to switch lookup to loop
          MoneroSubaddress tgtSubaddress = account.getSubaddresses().get(subaddress.getIndex());
          assertEquals("RPC subaddresses are out of order", tgtSubaddress.getIndex(), subaddress.getIndex());
          if (subaddress.getBalance() != null) tgtSubaddress.setBalance(subaddress.getBalance());
          if (subaddress.getUnlockedBalance() != null) tgtSubaddress.setUnlockedBalance(subaddress.getUnlockedBalance());
          if (subaddress.getNumUnspentOutputs() != null) tgtSubaddress.setNumUnspentOutputs(subaddress.getNumUnspentOutputs());
        }
      }
    }
    
    // return accounts
    return accounts;
  }

  // TODO: getAccountByIndex(), getAccountByTag()
  @Override
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses) {
    return getAccount(accountIdx, includeSubaddresses, false);
  }
  
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses, boolean skipBalances) {
    if (accountIdx < 0) throw new MoneroException("Account index must be greater than or equal to 0");
    for (MoneroAccount account : getAccounts()) {
      if (account.getIndex() == accountIdx) {
        if (includeSubaddresses) account.setSubaddresses(getSubaddresses(accountIdx, null, skipBalances));
        return account;
      }
    }
    throw new MoneroException("Account with index " + accountIdx + " does not exist");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroAccount createAccount(String label) {
    label = label == null || label.isEmpty() ? null : label;
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("label", label);
    Map<String, Object> resp = rpc.sendJsonRequest("create_account", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return new MoneroAccount(((BigInteger) result.get("account_index")).intValue(), (String) result.get("address"), label, BigInteger.valueOf(0), BigInteger.valueOf(0), null);
  }
  
  @Override
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices) {
    return getSubaddresses(accountIdx, subaddressIndices, false);
  }

  @SuppressWarnings("unchecked")
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices, boolean skipBalances) {
    
    // fetch subaddresses
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    if (subaddressIndices != null && !subaddressIndices.isEmpty()) params.put("address_index", subaddressIndices);
    Map<String, Object> resp = rpc.sendJsonRequest("get_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // initialize subaddresses
    List<MoneroSubaddress> subaddresses = new ArrayList<MoneroSubaddress>();
    for (Map<String, Object> rpcSubaddress : (List<Map<String, Object>>) result.get("addresses")) {
      MoneroSubaddress subaddress = convertRpcSubaddress(rpcSubaddress);
      subaddress.setAccountIndex(accountIdx);
      subaddresses.add(subaddress);
    }
    
    // fetch and initialize subaddress balances
    if (!skipBalances) {
      
      // these fields are not initialized if subaddress is unused and therefore not returned from `get_balance`
      for (MoneroSubaddress subaddress : subaddresses) {
        subaddress.setBalance(BigInteger.valueOf(0));
        subaddress.setUnlockedBalance(BigInteger.valueOf(0));
        subaddress.setNumUnspentOutputs(0);
        subaddress.setNumBlocksToUnlock(0);
      }

      // fetch and initialize balances
      resp = rpc.sendJsonRequest("get_balance", params);
      result = (Map<String, Object>) resp.get("result");
      if (result.containsKey("per_subaddress")) {
        for (Map<String, Object> rpcSubaddress : (List<Map<String, Object>>) result.get("per_subaddress")) {
          MoneroSubaddress subaddress = convertRpcSubaddress(rpcSubaddress);
          
          // transfer info to existing subaddress object
          for (MoneroSubaddress tgtSubaddress : subaddresses) {
            if (!tgtSubaddress.getIndex().equals(subaddress.getIndex())) continue; // skip to subaddress with same index
            if (subaddress.getBalance() != null) tgtSubaddress.setBalance(subaddress.getBalance());
            if (subaddress.getUnlockedBalance() != null) tgtSubaddress.setUnlockedBalance(subaddress.getUnlockedBalance());
            if (subaddress.getNumUnspentOutputs() != null) tgtSubaddress.setNumUnspentOutputs(subaddress.getNumUnspentOutputs());
            if (subaddress.getNumBlocksToUnlock() != null) tgtSubaddress.setNumBlocksToUnlock(subaddress.getNumBlocksToUnlock());
          }
        }
      }
    }
    
    // cache addresses
    Map<Integer, String> subaddressMap = addressCache.get(accountIdx);
    if (subaddressMap == null) {
      subaddressMap = new HashMap<Integer, String>();
      addressCache.put(accountIdx, subaddressMap);
    }
    for (MoneroSubaddress subaddress : subaddresses) {
      subaddressMap.put(subaddress.getIndex(), subaddress.getAddress());
    }
    
    // return results
    return subaddresses;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroSubaddress createSubaddress(int accountIdx, String label) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    params.put("label", label);
    Map<String, Object> resp = rpc.sendJsonRequest("create_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // build subaddress object
    MoneroSubaddress subaddress = new MoneroSubaddress();
    subaddress.setAccountIndex(accountIdx);
    subaddress.setIndex(((BigInteger) result.get("address_index")).intValue());
    subaddress.setAddress((String) result.get("address"));
    subaddress.setLabel(label);
    subaddress.setBalance(BigInteger.valueOf(0));
    subaddress.setUnlockedBalance(BigInteger.valueOf(0));
    subaddress.setNumUnspentOutputs(0);
    subaddress.setIsUsed(false);
    subaddress.setNumBlocksToUnlock(0);
    return subaddress;
  }

  @Override
  public String getAddress(int accountIdx, int subaddressIdx) {
    Map<Integer, String> subaddressMap = addressCache.get(accountIdx);
    if (subaddressMap == null) {
      getSubaddresses(accountIdx, null, true);      // cache's all addresses at this account
      return getAddress(accountIdx, subaddressIdx); // uses cache
    }
    String address = subaddressMap.get(subaddressIdx);
    if (address == null) {
      getSubaddresses(accountIdx, null, true);      // cache's all addresses at this account
      return addressCache.get(accountIdx).get(subaddressIdx);
    }
    return address;
  }

  // TODO: use cache
  @SuppressWarnings("unchecked")
  @Override
  public MoneroSubaddress getAddressIndex(String address) {
    
    // fetch result and normalize error if address does not belong to the wallet
    Map<String, Object> result;
    try {
      Map<String, Object> params =  new HashMap<String, Object>();
      params.put("address", address);
      Map<String, Object> resp = rpc.sendJsonRequest("get_address_index", params);
      result = (Map<String, Object>) resp.get("result");
    } catch (MoneroRpcException e) {
      if (e.getCode() == -2) throw new MoneroException("Address does not belong to the wallet");
      throw e;
    }
    
    // convert rpc response
    Map<String, BigInteger> rpcIndices = (Map<String, BigInteger>) result.get("index");
    MoneroSubaddress subaddress = new MoneroSubaddress(address);
    subaddress.setAccountIndex(rpcIndices.get("major").intValue());
    subaddress.setIndex(rpcIndices.get("minor").intValue());
    return subaddress;
  }


  @Override
  public BigInteger getBalance() {
    return getBalances(null, null)[0];
  }

  @Override
  public BigInteger getBalance(int accountIdx) {
    return getBalances(accountIdx, null)[0];
  }

  @Override
  public BigInteger getBalance(int accountIdx, int subaddressIdx) {
    return getBalances(accountIdx, subaddressIdx)[0];
  }

  @Override
  public BigInteger getUnlockedBalance() {
    return getBalances(null, null)[1];
  }

  @Override
  public BigInteger getUnlockedBalance(int accountIdx) {
    return getBalances(accountIdx, null)[1];
  }

  @Override
  public BigInteger getUnlockedBalance(int accountIdx, int subaddressIdx) {
    return getBalances(accountIdx, subaddressIdx)[1];
  }

  @Override
  public List<MoneroTxWallet> getTxs(MoneroTxRequest request) {
    
    // normalize tx request
    if (request == null) request = new MoneroTxRequest();
    if (request.getTransferRequest() == null) request.setTransferRequest(new MoneroTransferRequest());
    MoneroTransferRequest transferRequest = request.getTransferRequest();
    
    // temporarily disable transfer request
    request.setTransferRequest(null);
    
    // fetch all transfers that meet tx request
    List<MoneroTransfer> transfers = getTransfers(new MoneroTransferRequest().setTxRequest(request));
    
    // collect unique txs from transfers while retaining order
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    Set<MoneroTxWallet> txsSet = new HashSet<MoneroTxWallet>();
    for (MoneroTransfer transfer : transfers) {
      if (!txsSet.contains(transfer.getTx())) {
        txs.add(transfer.getTx());
        txsSet.add(transfer.getTx());
      }
    }
    
    // fetch and merge outputs if requested
    if (Boolean.TRUE.equals(request.getIncludeOutputs())) {
      List<MoneroOutputWallet> outputs = getOutputs(new MoneroOutputRequest().setTxRequest(request));
      
      // merge output txs one time while retaining order
      Set<MoneroTxWallet> outputTxs = new HashSet<MoneroTxWallet>();
      for (MoneroOutputWallet output : outputs) {
        if (!outputTxs.contains(output.getTx())){
          mergeTx(txs, output.getTx(), true);
          outputTxs.add(output.getTx());
        }
      }
    }
    
    // filter txs that don't meet transfer request
    request.setTransferRequest(transferRequest);
    txs = Filter.apply(request, txs);
    
    // verify all specified tx ids found
    if (request.getTxIds() != null) {
      for (String txId : request.getTxIds()) {
        boolean found = false;
        for (MoneroTxWallet tx : txs) {
          if (txId.equals(tx.getId())) {
            found = true;
            break;
          }
        }
        if (!found) throw new MoneroException("Tx not found in wallet: " + txId);
      }
    }
    
    // special case: re-fetch txs if inconsistency caused by needing to make multiple rpc calls
    for (MoneroTxWallet tx : txs) {
      if (tx.getIsConfirmed() && tx.getBlock() == null) return getTxs(request);
    }
    
    // otherwise order txs if tx ids given then return
    if (request.getTxIds() != null && !request.getTxIds().isEmpty()) {
      Map<String, MoneroTxWallet> txsById = new HashMap<String, MoneroTxWallet>();  // store txs in temporary map for sorting
      for (MoneroTxWallet tx : txs) txsById.put(tx.getId(), tx);
      List<MoneroTxWallet> orderedTxs = new ArrayList<MoneroTxWallet>();
      for (String txId : request.getTxIds()) if (txsById.containsKey(txId)) orderedTxs.add(txsById.get(txId));
      txs = orderedTxs;
    }
    return txs;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroTransfer> getTransfers(MoneroTransferRequest request) {
    
    // normalize transfer request
    if (request == null) request = new MoneroTransferRequest();
    if (request.getTxRequest() == null) request.setTxRequest(new MoneroTxRequest());
    MoneroTxRequest txReq = request.getTxRequest();
    
    // build params for get_transfers rpc call
    Map<String, Object> params = new HashMap<String, Object>();
    boolean canBeConfirmed = !Boolean.FALSE.equals(txReq.getIsConfirmed()) && !Boolean.TRUE.equals(txReq.getInTxPool()) && !Boolean.TRUE.equals(txReq.getIsFailed()) && !Boolean.FALSE.equals(txReq.getIsRelayed());
    boolean canBeInTxPool = !Boolean.TRUE.equals(txReq.getIsConfirmed()) && !Boolean.FALSE.equals(txReq.getInTxPool()) && !Boolean.TRUE.equals(txReq.getIsFailed()) && !Boolean.FALSE.equals(txReq.getIsRelayed()) && txReq.getHeight() == null && txReq.getMinHeight() == null;
    boolean canBeIncoming = !Boolean.FALSE.equals(request.getIsIncoming()) && !Boolean.TRUE.equals(request.getIsOutgoing()) && !Boolean.TRUE.equals(request.getHasDestinations());
    boolean canBeOutgoing = !Boolean.FALSE.equals(request.getIsOutgoing()) && !Boolean.TRUE.equals(request.getIsIncoming());
    params.put("in", canBeIncoming && canBeConfirmed);
    params.put("out", canBeOutgoing && canBeConfirmed);
    params.put("pool", canBeIncoming && canBeInTxPool);
    params.put("pending", canBeOutgoing && canBeInTxPool);
    params.put("failed", !Boolean.FALSE.equals(txReq.getIsFailed()) && !Boolean.TRUE.equals(txReq.getIsConfirmed()) && !Boolean.TRUE.equals(txReq.getInTxPool()));
    if (txReq.getMinHeight() != null) params.put("min_height", txReq.getMinHeight()); 
    if (txReq.getMaxHeight() != null) params.put("max_height", txReq.getMaxHeight());
    params.put("filter_by_height", txReq.getMinHeight() != null || txReq.getMaxHeight() != null);
    if (request.getAccountIndex() == null) {
      assertTrue("Filter specifies a subaddress index but not an account index", request.getSubaddressIndex() == null && request.getSubaddressIndices() == null);
      params.put("all_accounts", true);
    } else {
      params.put("account_index", request.getAccountIndex());
      
      // set subaddress indices param
      Set<Integer> subaddressIndices = new HashSet<Integer>();
      if (request.getSubaddressIndex() != null) subaddressIndices.add(request.getSubaddressIndex());
      if (request.getSubaddressIndices() != null) {
        for (int subaddressIdx : request.getSubaddressIndices()) subaddressIndices.add(subaddressIdx);
      }
      if (!subaddressIndices.isEmpty()) params.put("subaddr_indices", new ArrayList<Integer>(subaddressIndices));
    }
    
    // build txs using `get_transfers`
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    Map<String, Object> resp = rpc.sendJsonRequest("get_transfers", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    for (String key : result.keySet()) {
      for (Map<String, Object> rpcTx :((List<Map<String, Object>>) result.get(key))) {
        MoneroTxWallet tx = convertRpcTxWalletWithTransfer(rpcTx, null, null);
        if (tx.getIsConfirmed()) assertTrue(tx.getBlock().getTxs().contains(tx));
//        if (tx.getId().equals("38436c710dfbebfb24a14cddfd430d422e7282bbe94da5e080643a1bd2880b44")) {
//          System.out.println(rpcTx);
//          System.out.println(tx.getOutgoingAmount().compareTo(BigInteger.valueOf(0)) == 0);
//        }
        
        // replace transfer amount with destination sum
        // TODO monero-wallet-rpc: confirmed tx from/to same account has amount 0 but cached transfers
        if (tx.getOutgoingTransfer() != null && Boolean.TRUE.equals(tx.getIsRelayed()) && !Boolean.TRUE.equals(tx.getIsFailed()) &&
            tx.getOutgoingTransfer().getDestinations() != null && tx.getOutgoingAmount().compareTo(BigInteger.valueOf(0)) == 0) {
          MoneroOutgoingTransfer outgoingTransfer = tx.getOutgoingTransfer();
          BigInteger transferTotal = BigInteger.valueOf(0);
          for (MoneroDestination destination : outgoingTransfer.getDestinations()) transferTotal = transferTotal.add(destination.getAmount());
          tx.getOutgoingTransfer().setAmount(transferTotal);
        }
        
        // merge tx
        mergeTx(txs, tx);
      }
    }
    
    // filter and return transfers
    List<MoneroTransfer> transfers = new ArrayList<MoneroTransfer>();
    for (MoneroTxWallet tx : txs) {
      if (request.meetsCriteria(tx.getOutgoingTransfer())) transfers.add(tx.getOutgoingTransfer());
      if (tx.getIncomingTransfers() != null) {
        transfers.addAll(Filter.apply(request, tx.getIncomingTransfers()));
      }
    }
    return transfers;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroOutputWallet> getOutputs(MoneroOutputRequest request) {
    
    // normalize output request
    if (request == null) request = new MoneroOutputRequest();
    if (request.getTxRequest() == null) request.setTxRequest(new MoneroTxRequest());
    
    // determine account and subaddress indices to be queried
    Map<Integer, List<Integer>> indices = new HashMap<Integer, List<Integer>>();
    if (request.getAccountIndex() != null) {
      Set<Integer> subaddressIndices = new HashSet<Integer>();
      if (request.getSubaddressIndex() != null) subaddressIndices.add(request.getSubaddressIndex());
      if (request.getSubaddressIndices() != null) for (int subaddressIdx : request.getSubaddressIndices()) subaddressIndices.add(subaddressIdx);
      indices.put(request.getAccountIndex(), subaddressIndices.isEmpty() ? null : new ArrayList<Integer>(subaddressIndices));  // null will fetch from all subaddresses
    } else {
      assertEquals("Request specifies a subaddress index but not an account index", null, request.getSubaddressIndex());
      assertTrue("Request specifies subaddress indices but not an account index", request.getSubaddressIndices() == null || request.getSubaddressIndices().size() == 0);
      indices = getAccountIndices(false);  // fetch all account indices without subaddresses
    }
    
    // collect txs with vouts for each indicated account using `incoming_transfers` rpc call
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    Map<String, Object> params = new HashMap<String, Object>();
    String transferType;
    if (Boolean.TRUE.equals(request.getIsSpent())) transferType = "unavailable";
    else if (Boolean.FALSE.equals(request.getIsSpent())) transferType = "available";
    else transferType = "all";
    params.put("transfer_type", transferType);
    params.put("verbose", true);
    for (int accountIdx : indices.keySet()) {
    
      // send request
      params.put("account_index", accountIdx);
      params.put("subaddr_indices", indices.get(accountIdx));
      Map<String, Object> resp = rpc.sendJsonRequest("incoming_transfers", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      
      // convert response to txs with vouts and merge
      if (!result.containsKey("transfers")) continue;
      for (Map<String, Object> rpcVout : (List<Map<String, Object>>) result.get("transfers")) {
        MoneroTxWallet tx = convertRpcTxWalletWithVout(rpcVout);
        mergeTx(txs, tx);
      }
    }
    
    // filter and return vouts
    List<MoneroOutputWallet> vouts = new ArrayList<MoneroOutputWallet>();
    for (MoneroTxWallet tx : txs) {
      vouts.addAll(Filter.apply(request, tx.getVoutsWallet()));
    }
    return vouts;
  }

  @Override
  public List<MoneroKeyImage> getKeyImages() {
    return rpcExportKeyImages(true);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroKeyImageImportResult importKeyImages(List<MoneroKeyImage> keyImages) {
    
    // convert key images to rpc parameter
    List<Map<String, Object>> rpcKeyImages = new ArrayList<Map<String, Object>>();
    for (MoneroKeyImage keyImage : keyImages) {
      Map<String, Object> rpcKeyImage = new HashMap<String, Object>();
      rpcKeyImage.put("key_image", keyImage.getHex());
      rpcKeyImage.put("signature", keyImage.getSignature());
      rpcKeyImages.add(rpcKeyImage);
    }
    
    // send rpc request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("signed_key_images", rpcKeyImages);
    Map<String, Object> resp = rpc.sendJsonRequest("import_key_images", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // build and return result
    MoneroKeyImageImportResult importResult = new MoneroKeyImageImportResult();
    importResult.setHeight(((BigInteger) result.get("height")).intValue());
    importResult.setSpentAmount((BigInteger) result.get("spent"));
    importResult.setUnspentAmount((BigInteger) result.get("unspent"));
    return importResult;
  }

  @Override
  public List<MoneroKeyImage> getNewKeyImagesFromLastImport() {
    return rpcExportKeyImages(false);
  }

  @Override
  public MoneroTxWallet send(MoneroSendRequest request) {
    
    // normalize request
    assertNotNull("Send request must not be null", request);
    if (request.getCanSplit() == null) request.setCanSplit(false);
    else assertEquals(false, request.getCanSplit());
    
    // send with common method
    return sendCommon(request).get(0);
  }

  @Override
  public List<MoneroTxWallet> sendSplit(MoneroSendRequest request) {
    
    // normalize request
    assertNotNull("Send request must not be null", request);
    if (request.getCanSplit() == null) request.setCanSplit(true);
    else assertEquals(true, request.getCanSplit());
    
    // send with common method
    return sendCommon(request);
  }
  
  @SuppressWarnings("unchecked")
  private List<MoneroTxWallet> sendCommon(MoneroSendRequest request) {
    
    // validate request
    assertNotNull(request.getDestinations());
    assertNotNull(request.getCanSplit());
    assertNull(request.getSweepEachSubaddress());
    assertNull(request.getBelowAmount());
    
    // determine account and subaddresses to send from
    Integer accountIdx = request.getAccountIndex();
    if (accountIdx == null) throw new MoneroException("Must specify the account index to send from");
    List<Integer> subaddressIndices = request.getSubaddressIndices() == null ? null : new ArrayList<Integer>(request.getSubaddressIndices()); // fetch all or copy given indices
    
    // build request parameters
    Map<String, Object> params = new HashMap<String, Object>();
    List<Map<String, Object>> destinationMaps = new ArrayList<Map<String, Object>>();
    params.put("destinations", destinationMaps);
    for (MoneroDestination destination : request.getDestinations()) {
      assertNotNull("Destination address is not defined", destination.getAddress());
      assertNotNull("Destination amount is not defined", destination.getAmount());
      Map<String, Object> destinationMap = new HashMap<String, Object>();
      destinationMap.put("address", destination.getAddress());
      destinationMap.put("amount", destination.getAmount().toString());
      destinationMaps.add(destinationMap);
    }
    params.put("account_index", accountIdx);
    params.put("subaddr_indices", subaddressIndices);
    params.put("payment_id", request.getPaymentId());
    params.put("mixin", request.getMixin());
    params.put("ring_size", request.getRingSize());
    params.put("unlock_time", request.getUnlockTime());
    params.put("do_not_relay", request.getDoNotRelay());
    params.put("priority", request.getPriority() == null ? null : request.getPriority().ordinal());
    params.put("get_tx_key", true);
    params.put("get_tx_hex", true);
    params.put("get_tx_metadata", true);
    
    // send request
    Map<String, Object> resp = rpc.sendJsonRequest(request.getCanSplit() ? "transfer_split" : "transfer", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // pre-initialize txs to return
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    if (!request.getCanSplit()) txs.add(new MoneroTxWallet());
    else {
      List<String> txHashes = (List<String>) result.get("tx_hash_list");
      for (int i = 0; i < txHashes.size(); i++) txs.add(new MoneroTxWallet());
    }
    
    // initialize known fields of txs
    for (MoneroTxWallet tx : txs) {
      initSentTxWallet(request, tx);
      tx.getOutgoingTransfer().setAccountIndex(accountIdx);
        if (subaddressIndices != null && subaddressIndices.size() == 1) tx.getOutgoingTransfer().setSubaddressIndices(subaddressIndices);
    }
    
    // initialize txs from rpc response
    if (request.getCanSplit()) convertRpcSentTxWallets(result, txs);
    else convertRpcTxWalletWithTransfer(result, txs.get(0), true);
    return txs;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroTxWallet sweepOutput(MoneroSendRequest request) {
    
    // validate request
    assertNull(request.getSweepEachSubaddress());
    assertNull(request.getBelowAmount());
    assertNull("Splitting is not applicable when sweeping output", request.getCanSplit());
    
    // build request parameters
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", request.getDestinations().get(0).getAddress());
    params.put("account_index", request.getAccountIndex());
    params.put("subaddr_indices", request.getSubaddressIndices());
    params.put("key_image", request.getKeyImage());
    params.put("mixin", request.getMixin());
    params.put("ring_size", request.getRingSize());
    params.put("unlock_time", request.getUnlockTime());
    params.put("do_not_relay", request.getDoNotRelay());
    params.put("priority", request.getPriority() == null ? null : request.getPriority().ordinal());
    params.put("payment_id", request.getPaymentId());
    params.put("get_tx_key", true);
    params.put("get_tx_hex", true);
    params.put("get_tx_metadata", true);
    
    // send request
    Map<String, Object> resp = (Map<String, Object>) rpc.sendJsonRequest("sweep_single", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");

    // build and return tx response
    MoneroTxWallet tx = initSentTxWallet(request, null);
    convertRpcTxWalletWithTransfer(result, tx, true);
    tx.getOutgoingTransfer().getDestinations().get(0).setAmount(tx.getOutgoingTransfer().getAmount());  // initialize destination amount
    return tx;
  }
  
  @Override
  public List<MoneroTxWallet> sweepAllUnlocked(MoneroSendRequest request) {
    
    // validate request
    if (request == null) throw new MoneroException("Must specify sweep request");
    if (request.getDestinations() == null || request.getDestinations().size() != 1) throw new MoneroException("Must specify exactly one destination to sweep to");
    if (request.getDestinations().get(0).getAddress() == null) throw new MoneroException("Must specify destination address to sweep to");
    if (request.getDestinations().get(0).getAmount() != null) throw new MoneroException("Cannot specify amount in sweep request");
    if (request.getKeyImage() != null) throw new MoneroException("Key image defined; use sweepOutput() to sweep an output by its key image");
    if (request.getSubaddressIndices() != null && request.getSubaddressIndices().isEmpty()) request.setSubaddressIndices((List<Integer>) null);
    if (request.getAccountIndex() == null && request.getSubaddressIndices() != null) throw new MoneroException("Must specify account index with subaddress indicies");
    
    // determine account and subaddress indices to sweep; default to all with unlocked balance if not specified
    LinkedHashMap<Integer, List<Integer>> indices = new LinkedHashMap<Integer, List<Integer>>();  // java type preserves insertion order
    if (request.getAccountIndex() != null) {
      if (request.getSubaddressIndices() != null) {
        indices.put(request.getAccountIndex(), request.getSubaddressIndices());
      } else {
        List<Integer> subaddressIndices = new ArrayList<Integer>();
        indices.put(request.getAccountIndex(), subaddressIndices);
        for (MoneroSubaddress subaddress : getSubaddresses(request.getAccountIndex())) {
          if (subaddress.getUnlockedBalance().compareTo(BigInteger.valueOf(0)) > 0) subaddressIndices.add(subaddress.getIndex());
        }
      }
    } else {
      List<MoneroAccount> accounts = getAccounts(true);
      for (MoneroAccount account : accounts) {
        if (account.getUnlockedBalance().compareTo(BigInteger.valueOf(0)) > 0) {
          List<Integer> subaddressIndices = new ArrayList<Integer>();
          indices.put(account.getIndex(), subaddressIndices);
          for (MoneroSubaddress subaddress : account.getSubaddresses()) {
            if (subaddress.getUnlockedBalance().compareTo(BigInteger.valueOf(0)) > 0) subaddressIndices.add(subaddress.getIndex());
          }
        }
      }
    }
    
    // sweep from each account and collect unique transactions
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    for (Integer accountIdx : indices.keySet()) {
      request.setAccountIndex(accountIdx);  // TODO: this modifies original request param; deep copy with new MoneroSendRequest(request)
      
      // sweep all subaddresses together  // TODO monero-wallet-rpc: doesn't this reveal outputs belong to same wallet?
      if (!Boolean.TRUE.equals(request.getSweepEachSubaddress())) {
        request.setSubaddressIndices(indices.get(accountIdx));
        try {
          txs.addAll(rpcSweepAll(request));
        } catch (MoneroException e) {
          // account cannot be swept
        }
      }
      
      // sweep each subaddress individually
      else {
        for (int subaddressIdx : indices.get(accountIdx)) {
          request.setSubaddressIndices(subaddressIdx);
          try {
            txs.addAll(rpcSweepAll(request));
          } catch (MoneroException e) {
            // subaddress cannot be swept
          }
        }
      }
    }
    
    // return sweep transactions
    return txs;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroTxWallet> sweepDust(boolean doNotRelay) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("do_not_relay", doNotRelay);
    Map<String, Object> resp = rpc.sendJsonRequest("sweep_dust", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    if (!result.containsKey("tx_hash_list")) return new ArrayList<MoneroTxWallet>();  // no dust to sweep
    List<MoneroTxWallet> txs = convertRpcSentTxWallets(result, null);
    for (MoneroTxWallet tx : txs) {
      tx.setIsRelayed(!doNotRelay);
      tx.setInTxPool(tx.getIsRelayed());
    }
    return txs;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> relayTxs(Collection<String> txMetadatas) {
    if (txMetadatas == null || txMetadatas.isEmpty()) throw new MoneroException("Must provide an array of tx metadata to relay");
    List<String> txIds = new ArrayList<String>();
    for (String txMetadata : txMetadatas) {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("hex", txMetadata);
      Map<String, Object> resp = rpc.sendJsonRequest("relay_tx", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      txIds.add((String) result.get("tx_hash"));
    }
    return txIds;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getTxNotes(Collection<String> txIds) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txids", txIds);
    Map<String, Object> resp = rpc.sendJsonRequest("get_tx_notes", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (List<String>) result.get("notes");
  }

  @Override
  public void setTxNotes(Collection<String> txIds, Collection<String> notes) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txids", txIds);
    params.put("notes", notes);
    rpc.sendJsonRequest("set_tx_notes", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public String sign(String msg) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("data", msg);
    Map<String, Object> resp = rpc.sendJsonRequest("sign", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean verify(String msg, String address, String signature) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("data", msg);
    params.put("address", address);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("verify", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (boolean) result.get("good");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getTxKey(String txId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    Map<String, Object> resp = rpc.sendJsonRequest("get_tx_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("tx_key");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroCheckTx checkTxKey(String txId, String txKey, String address) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("tx_key", txKey);
    params.put("address", address);
    Map<String, Object> resp = rpc.sendJsonRequest("check_tx_key", params);
    
    // interpret result
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroCheckTx check = new MoneroCheckTx();
    check.setIsGood(true);
    check.setNumConfirmations(((BigInteger) result.get("confirmations")).intValue());
    check.setInTxPool((Boolean) result.get("in_pool"));
    check.setReceivedAmount((BigInteger) result.get("received"));
    return check;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getTxProof(String txId, String address, String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("address", address);
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_tx_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroCheckTx checkTxProof(String txId, String address, String message, String signature) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("address", address);
    params.put("message", message);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("check_tx_proof", params);
    
    // interpret response
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    boolean isGood = (boolean) result.get("good");
    MoneroCheckTx check = new MoneroCheckTx();
    check.setIsGood(isGood);
    if (isGood) {
      check.setNumConfirmations(((BigInteger) result.get("confirmations")).intValue());
      check.setInTxPool((boolean) result.get("in_pool"));
      check.setReceivedAmount((BigInteger) result.get("received"));
    }
    return check;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getSpendProof(String txId, String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_spend_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean checkSpendProof(String txId, String message, String signature) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("message", message);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("check_spend_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (boolean) result.get("good");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getReserveProofWallet(String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("all", true);
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_reserve_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    params.put("amount", amount.toString());
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_reserve_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroCheckReserve checkReserveProof(String address, String message, String signature) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", address);
    params.put("message", message);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("check_reserve_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // interpret results
    boolean isGood = (boolean) result.get("good");
    MoneroCheckReserve check = new MoneroCheckReserve();
    check.setIsGood(isGood);
    if (isGood) {
      check.setSpentAmount((BigInteger) result.get("spent"));
      check.setTotalAmount((BigInteger) result.get("total"));
    }
    return check;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroAddressBookEntry> getAddressBookEntries(Collection<Integer> entryIndices) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("entries", entryIndices);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_address_book", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    List<MoneroAddressBookEntry> entries = new ArrayList<MoneroAddressBookEntry>();
    if (!resultMap.containsKey("entries")) return entries;
    for (Map<String, Object> entryMap : (List<Map<String, Object>>) resultMap.get("entries")) {
      MoneroAddressBookEntry entry = new MoneroAddressBookEntry(
              ((BigInteger) entryMap.get("index")).intValue(),
              (String) entryMap.get("address"),
              (String) entryMap.get("payment_id"),
              (String) entryMap.get("description")
      );
      entries.add(entry);
    }
    return entries;
  }

  @SuppressWarnings("unchecked")
  @Override
  public int addAddressBookEntry(String address, String description, String paymentId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", address);
    params.put("payment_id", paymentId);
    params.put("description", description);
    Map<String, Object> respMap = rpc.sendJsonRequest("add_address_book", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    return ((BigInteger) resultMap.get("index")).intValue();
  }

  @Override
  public void deleteAddressBookEntry(int entryIdx) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("index", entryIdx);
    rpc.sendJsonRequest("delete_address_book", params);
  }
  
  @Override
  public void tagAccounts(String tag, Collection<Integer> accountIndices) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tag", tag);
    params.put("accounts", accountIndices);
    rpc.sendJsonRequest("tag_accounts", params);
  }

  @Override
  public void untagAccounts(Collection<Integer> accountIndices) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("accounts", accountIndices);
    rpc.sendJsonRequest("untag_accounts", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroAccountTag> getAccountTags() {
    List<MoneroAccountTag> tags = new ArrayList<MoneroAccountTag>();
    Map<String, Object> respMap = rpc.sendJsonRequest("get_account_tags");
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    List<Map<String, Object>> accountTagMaps = (List<Map<String, Object>>) resultMap.get("account_tags");
    if (accountTagMaps != null) {
      for (Map<String, Object> accountTagMap : accountTagMaps) {
        MoneroAccountTag tag = new MoneroAccountTag();
        tags.add(tag);
        tag.setTag((String) accountTagMap.get("tag"));
        tag.setLabel((String) accountTagMap.get("label"));
        List<BigInteger> accountIndicesBI = (List<BigInteger>) accountTagMap.get("accounts");
        List<Integer> accountIndices = new ArrayList<Integer>();
        for (BigInteger idx : accountIndicesBI) accountIndices.add(idx.intValue());
        tag.setAccountIndices(accountIndices);
      }
    }
    return tags;
  }

  @Override
  public void setAccountTagLabel(String tag, String label) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tag", tag);
    params.put("description", label);
    rpc.sendJsonRequest("set_account_tag_description", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public String createPaymentUri(MoneroSendRequest request) {
    assertNotNull("Must provide send request to create a payment URI", request);
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", request.getDestinations().get(0).getAddress());
    params.put("amount", request.getDestinations().get(0).getAmount() != null ? request.getDestinations().get(0).getAmount().toString() : null);
    params.put("payment_id", request.getPaymentId());
    params.put("recipient_name", request.getRecipientName());
    params.put("tx_description", request.getNote());
    Map<String, Object> resp = rpc.sendJsonRequest("make_uri", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("uri");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroSendRequest parsePaymentUri(String uri) {
    assertNotNull("Must provide URI to parse", uri);
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("uri", uri);
    Map<String, Object> resp = rpc.sendJsonRequest("parse_uri", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    Map<String, Object> rpcUri = (Map<String, Object>) result.get("uri");
    MoneroSendRequest request = new MoneroSendRequest((String) rpcUri.get("address"), (BigInteger) rpcUri.get("amount"));
    request.setPaymentId((String) rpcUri.get("payment_id"));
    request.setRecipientName((String) rpcUri.get("recipient_name"));
    request.setNote((String) rpcUri.get("tx_description"));
    if ("".equals(request.getDestinations().get(0).getAddress())) request.getDestinations().get(0).setAddress(null);
    if ("".equals(request.getPaymentId())) request.setPaymentId(null);
    if ("".equals(request.getRecipientName())) request.setRecipientName(null);
    if ("".equals(request.getNote())) request.setNote(null);
    return request;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getOutputsHex() {
    Map<String, Object> resp = rpc.sendJsonRequest("export_outputs");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("outputs_data_hex");
  }

  @SuppressWarnings("unchecked")
  @Override
  public int importOutputsHex(String outputsHex) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("outputs_data_hex", outputsHex);
    Map<String, Object> resp = rpc.sendJsonRequest("import_outputs", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return ((BigInteger) result.get("num_imported")).intValue();
  }

  @Override
  public void setAttribute(String key, String val) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key", key);
    params.put("value", val);
    rpc.sendJsonRequest("set_attribute", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getAttribute(String key) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key", key);
    Map<String, Object> resp = rpc.sendJsonRequest("get_attribute", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("value");
  }

  @Override
  public void startMining(Integer numThreads, Boolean backgroundMining, Boolean ignoreBattery) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("threads_count", numThreads);
    params.put("backgroundMining", backgroundMining);
    params.put("ignoreBattery", ignoreBattery);
    rpc.sendJsonRequest("start_mining", params);
  }

  @Override
  public void stopMining() {
    rpc.sendJsonRequest("stop_mining");
  }
  
  // ------------------------------ PRIVATE -----------------------------------
  
  private Map<Integer, List<Integer>> getAccountIndices(boolean getSubaddressIndices) {
    Map<Integer, List<Integer>> indices = new HashMap<Integer, List<Integer>>();
    for (MoneroAccount account : getAccounts()) {
      indices.put(account.getIndex(), getSubaddressIndices ? getSubaddressIndices(account.getIndex()) : null);
    }
    return indices;
  }
  
  @SuppressWarnings("unchecked")
  private List<Integer> getSubaddressIndices(int accountIdx) {
    List<Integer> subaddressIndices = new ArrayList<Integer>();
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    Map<String, Object> resp = rpc.sendJsonRequest("get_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    for (Map<String, Object> address : (List<Map<String, Object>>) result.get("addresses")) {
      subaddressIndices.add(((BigInteger) address.get("address_index")).intValue());
    }
    return subaddressIndices;
  }
  
  /**
   * Common method to get key images.
   * 
   * @param all specifies to get all xor only new images from last import
   * @return {MoneroKeyImage[]} are the key images
   */
  @SuppressWarnings("unchecked")
  private List<MoneroKeyImage> rpcExportKeyImages(boolean all) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("all", all);
    Map<String, Object> resp = rpc.sendJsonRequest("export_key_images", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    List<MoneroKeyImage> images = new ArrayList<MoneroKeyImage>();
    if (!result.containsKey("signed_key_images")) return images;
    for (Map<String, Object> rpcImage : (List<Map<String, Object>>) result.get("signed_key_images")) {
      images.add(new MoneroKeyImage((String) rpcImage.get("key_image"), (String) rpcImage.get("signature")));
    }
    return images;
  }
  
  @SuppressWarnings("unchecked")
  private BigInteger[] getBalances(Integer accountIdx, Integer subaddressIdx) {
    if (accountIdx == null) {
      assertNull("Must provide account index with subaddress index", subaddressIdx);
      BigInteger balance = BigInteger.valueOf(0);
      BigInteger unlockedBalance = BigInteger.valueOf(0);
      for (MoneroAccount account : getAccounts()) {
        balance = balance.add(account.getBalance());
        unlockedBalance = unlockedBalance.add(account.getUnlockedBalance());
      }
      return new BigInteger[] { balance, unlockedBalance };
    } else {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("account_index", accountIdx);
      params.put("address_indices", subaddressIdx == null ? null : new Integer[] { subaddressIdx });
      Map<String, Object> resp = rpc.sendJsonRequest("get_balance", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      if (subaddressIdx == null) return new BigInteger[] { (BigInteger) result.get("balance"), (BigInteger) result.get("unlocked_balance") };
      else {
        List<Map<String, Object>> rpcBalancesPerSubaddress = (List<Map<String, Object>>) result.get("per_subaddress");
        return new BigInteger[] { (BigInteger) rpcBalancesPerSubaddress.get(0).get("balance"), (BigInteger) rpcBalancesPerSubaddress.get(0).get("unlocked_balance") };
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  private List<MoneroTxWallet> rpcSweepAll(MoneroSendRequest request) {
    
    // validate request
    if (request == null) throw new MoneroException("Must specify sweep request");
    if (request.getAccountIndex() == null) throw new MoneroException("Must specify an account index to sweep from");
    if (request.getDestinations() == null || request.getDestinations().size() != 1) throw new MoneroException("Must specify exactly one destination to sweep to");
    if (request.getDestinations().get(0).getAddress() == null) throw new MoneroException("Must specify destination address to sweep to");
    if (request.getDestinations().get(0).getAmount() != null) throw new MoneroException("Cannot specify amount in sweep request");
    if (request.getKeyImage() != null) throw new MoneroException("Key image defined; use sweepOutput() to sweep an output by its key image");
    if (request.getSubaddressIndices() != null && request.getSubaddressIndices().isEmpty()) request.setSubaddressIndices((List<Integer>) null);
    
    // sweep from all subaddresses if not otherwise defined
    if (request.getSubaddressIndices() == null) {
      request.setSubaddressIndices(new ArrayList<Integer>());
      for (MoneroSubaddress subaddress : getSubaddresses(request.getAccountIndex())) {
        request.getSubaddressIndices().add(subaddress.getIndex());
      }
    }
    if (request.getSubaddressIndices().size() == 0) throw new MoneroException("No subaddresses to sweep from");
    
    // common request params
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", request.getAccountIndex());
    params.put("subaddr_indices", request.getSubaddressIndices());
    params.put("address", request.getDestinations().get(0).getAddress());
    params.put("priority", request.getPriority() == null ? null : request.getPriority().ordinal());
    params.put("mixin", request.getMixin());
    params.put("ring_size", request.getRingSize());
    params.put("unlock_time", request.getUnlockTime());
    params.put("payment_id", request.getPaymentId());
    params.put("do_not_relay", request.getDoNotRelay());
    params.put("below_amount", request.getBelowAmount());
    params.put("get_tx_keys", true);
    params.put("get_tx_hex", true);
    params.put("get_tx_metadata", true);
    
    // invoke wallet rpc `sweep_all`
    Map<String, Object> resp = rpc.sendJsonRequest("sweep_all", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // initialize txs from response
    List<MoneroTxWallet> txs = convertRpcSentTxWallets(result, null);
    
    // initialize remaining known fields
    for (MoneroTxWallet tx : txs) {
      tx.setIsConfirmed(false);
      tx.setNumConfirmations(0);
      tx.setInTxPool(Boolean.TRUE.equals(request.getDoNotRelay()) ? false : true);
      tx.setDoNotRelay(Boolean.TRUE.equals(request.getDoNotRelay()) ? true : false);
      tx.setIsRelayed(!tx.getDoNotRelay());
      tx.setIsCoinbase(false);
      tx.setIsFailed(false);
      tx.setMixin(request.getMixin());
      MoneroOutgoingTransfer transfer = tx.getOutgoingTransfer();
      transfer.setAccountIndex(request.getAccountIndex());
      if (request.getSubaddressIndices().size() == 1) transfer.setSubaddressIndices(new ArrayList<Integer>(request.getSubaddressIndices()));
      MoneroDestination destination = new MoneroDestination(request.getDestinations().get(0).getAddress(), transfer.getAmount());
      transfer.setDestinations(Arrays.asList(destination));
      tx.setOutgoingTransfer(transfer);
      tx.setPaymentId(request.getPaymentId());
      if (tx.getUnlockTime() == null) tx.setUnlockTime(request.getUnlockTime() == null ? 0 : request.getUnlockTime());
      if (!tx.getDoNotRelay()) {
        if (tx.getLastRelayedTimestamp() == null) tx.setLastRelayedTimestamp(System.currentTimeMillis());  // TODO (monero-wallet-rpc): provide timestamp on response; unconfirmed timestamps vary
        if (tx.getIsDoubleSpend() == null) tx.setIsDoubleSpend(false);
      }
    }
    return txs;
  }
  
  // ---------------------------- PRIVATE STATIC ------------------------------
  
  private static MoneroAccount convertRpcAccount(Map<String, Object> rpcAccount) {
    MoneroAccount account = new MoneroAccount();
    for (String key : rpcAccount.keySet()) {
      Object val = rpcAccount.get(key);
      if (key.equals("account_index")) account.setIndex(((BigInteger) val).intValue());
      else if (key.equals("balance")) account.setBalance((BigInteger) val);
      else if (key.equals("unlocked_balance")) account.setUnlockedBalance((BigInteger) val);
      else if (key.equals("base_address")) account.setPrimaryAddress((String) val);
      else if (key.equals("label")) { if (!"".equals(val)) account.setLabel((String) val); }
      else if (key.equals("tag")) account.setTag((String) val);
      else LOGGER.warn("WARNING: ignoring unexpected account field: " + key + ": " + val);
    }
    return account;
  }
  
  private static MoneroSubaddress convertRpcSubaddress(Map<String, Object> rpcSubaddress) {
    MoneroSubaddress subaddress = new MoneroSubaddress();
    for (String key : rpcSubaddress.keySet()) {
      Object val = rpcSubaddress.get(key);
      if (key.equals("account_index")) subaddress.setAccountIndex(((BigInteger) val).intValue());
      else if (key.equals("address_index")) subaddress.setIndex(((BigInteger) val).intValue());
      else if (key.equals("address")) subaddress.setAddress((String) val);
      else if (key.equals("balance")) subaddress.setBalance((BigInteger) val);
      else if (key.equals("unlocked_balance")) subaddress.setUnlockedBalance((BigInteger) val);
      else if (key.equals("num_unspent_outputs")) subaddress.setNumUnspentOutputs(((BigInteger) val).intValue());
      else if (key.equals("label")) { if (!"".equals(val)) subaddress.setLabel((String) val); }
      else if (key.equals("used")) subaddress.setIsUsed((Boolean) val);
      else if (key.equals("blocks_to_unlock")) subaddress.setNumBlocksToUnlock(((BigInteger) val).intValue());
      else LOGGER.warn("WARNING: ignoring unexpected subaddress field: " + key + ": " + val);
    }
    return subaddress;
  }
  
  /**
   * Initializes a sent transaction.
   * 
   * @param request is the send configuration
   * @param tx is an existing transaction to initialize (optional)
   * @return tx is the initialized send tx
   */
  private static MoneroTxWallet initSentTxWallet(MoneroSendRequest request, MoneroTxWallet tx) {
    if (tx == null) tx = new MoneroTxWallet();
    tx.setIsConfirmed(false);
    tx.setNumConfirmations(0);
    tx.setInTxPool(Boolean.TRUE.equals(request.getDoNotRelay()) ? false : true);
    tx.setDoNotRelay(Boolean.TRUE.equals(request.getDoNotRelay()) ? true : false);
    tx.setIsRelayed(!Boolean.TRUE.equals(tx.getDoNotRelay()));
    tx.setIsCoinbase(false);
    tx.setIsFailed(false);
    tx.setMixin(request.getMixin());
    MoneroOutgoingTransfer transfer = new MoneroOutgoingTransfer().setTx(tx);
    if (request.getSubaddressIndices() != null && request.getSubaddressIndices().size() == 1) transfer.setSubaddressIndices(new ArrayList<Integer>(request.getSubaddressIndices())); // we know src subaddress indices iff request specifies 1
    List<MoneroDestination> destCopies = new ArrayList<MoneroDestination>();
    for (MoneroDestination dest : request.getDestinations()) destCopies.add(dest.copy());
    transfer.setDestinations(destCopies);
    tx.setOutgoingTransfer(transfer);
    tx.setPaymentId(request.getPaymentId());
    if (tx.getUnlockTime() == null) tx.setUnlockTime(request.getUnlockTime() == null ? 0 : request.getUnlockTime());
    if (!Boolean.TRUE.equals(tx.getDoNotRelay())) {
      if (tx.getLastRelayedTimestamp() == null) tx.setLastRelayedTimestamp(System.currentTimeMillis());  // TODO (monero-wallet-rpc): provide timestamp on response; unconfirmed timestamps vary
      if (tx.getIsDoubleSpend() == null) tx.setIsDoubleSpend(false);
    }
    return tx;
  }
  
  /**
   * Initializes sent MoneroTxWallet[] from a list of rpc txs.
   * 
   * @param rpcTxs are sent rpc txs to initialize the MoneroTxWallets from
   * @param txs are existing txs to initialize (optional)
   */
  @SuppressWarnings("unchecked")
  private static List<MoneroTxWallet> convertRpcSentTxWallets(Map<String, Object> rpcTxs, List<MoneroTxWallet> txs) {
    
    // get lists
    List<String> ids = (List<String>) rpcTxs.get("tx_hash_list");
    List<String> keys = (List<String>) rpcTxs.get("tx_key_list");
    List<String> blobs = (List<String>) rpcTxs.get("tx_blob_list");
    List<String> metadatas = (List<String>) rpcTxs.get("tx_metadata_list");
    List<BigInteger> fees = (List<BigInteger>) rpcTxs.get("fee_list");
    List<BigInteger> amounts = (List<BigInteger>) rpcTxs.get("amount_list");
    
    // ensure all lists are the same size
    Set<Integer> sizes = new HashSet<Integer>(Arrays.asList(ids.size(), blobs.size(), metadatas.size(), fees.size(), amounts.size()));
    assertEquals("RPC lists are different sizes", 1, sizes.size());
    
    // pre-initialize txs if necessary
    if (txs == null) {
      txs = new ArrayList<MoneroTxWallet>();
      for (int i = 0; i < ids.size(); i++) txs.add(new MoneroTxWallet());
    }
    
    // build transactions
    for (int i = 0; i < ids.size(); i++) {
      MoneroTxWallet tx = txs.get(i);
      tx.setId(ids.get(i));
      if (keys != null) tx.setKey(keys.get(i));
      tx.setFullHex(blobs.get(i));
      tx.setMetadata(metadatas.get(i));
      tx.setFee((BigInteger) fees.get(i));
      if (tx.getOutgoingTransfer() != null) tx.getOutgoingTransfer().setAmount((BigInteger) amounts.get(i));
      else tx.setOutgoingTransfer(new MoneroOutgoingTransfer().setTx(tx).setAmount((BigInteger) amounts.get(i)));
    }
    return txs;
  }
  
  /**
   * Builds a MoneroTxWallet from a RPC tx.
   * 
   * @param rpcTx is the rpc tx to build from
   * @param tx is an existing tx to continue initializing (optional)
   * @param isOutgoing specifies if the tx is outgoing if true, incoming if false, or decodes from type if undefined
   * @returns {MoneroTxWallet} is the initialized tx
   */
  @SuppressWarnings("unchecked")
  private static MoneroTxWallet convertRpcTxWalletWithTransfer(Map<String, Object> rpcTx, MoneroTxWallet tx, Boolean isOutgoing) {  // TODO: change everything to safe set
    
    // initialize tx to return
    if (tx == null) tx = new MoneroTxWallet();
    
    // initialize tx state from rpc type
    if (rpcTx.containsKey("type")) isOutgoing = decodeRpcType((String) rpcTx.get("type"), tx);
    else {
      assertNotNull("Must indicate if tx is outgoing (true) xor incoming (false) since unknown", isOutgoing);
      assertNotNull(tx.getIsConfirmed());
      assertNotNull(tx.getInTxPool());
      assertNotNull(tx.getIsCoinbase());
      assertNotNull(tx.getIsFailed());
      assertNotNull(tx.getDoNotRelay());
    }
    
    // TODO: safe set
    // initialize remaining fields  TODO: seems this should be part of common function with DaemonRpc._convertRpcTx
    MoneroBlockHeader header = null;
    MoneroTransfer transfer = null;
    for (String key : rpcTx.keySet()) {
      Object val = rpcTx.get(key);
      if (key.equals("txid")) tx.setId((String) val);
      else if (key.equals("tx_hash")) tx.setId((String) val);
      else if (key.equals("fee")) tx.setFee((BigInteger) val);
      else if (key.equals("note")) { if (!"".equals(val)) tx.setNote((String) val); }
      else if (key.equals("tx_key")) tx.setKey((String) val);
      else if (key.equals("type")) { } // type already handled
      else if (key.equals("tx_size")) tx.setSize(((BigInteger) val).intValue());
      else if (key.equals("unlock_time")) tx.setUnlockTime(((BigInteger) val).intValue());
      else if (key.equals("tx_blob")) tx.setFullHex((String) val);
      else if (key.equals("tx_metadata")) tx.setMetadata((String) val);
      else if (key.equals("double_spend_seen")) tx.setIsDoubleSpend((Boolean) val);
      else if (key.equals("block_height") || key.equals("height")) {
        if (tx.getIsConfirmed()) {
          if (header == null) header = new MoneroBlockHeader();
          header.setHeight(((BigInteger) val).longValue());
        }
      }
      else if (key.equals("timestamp")) {
        if (tx.getIsConfirmed()) {
          if (header == null) header = new MoneroBlockHeader();
          header.setTimestamp(((BigInteger) val).longValue());
        } else {
          // timestamp of unconfirmed tx is current request time
        }
      }
      else if (key.equals("confirmations")) {
        if (!tx.getIsConfirmed()) tx.setNumConfirmations(0);
        else tx.setNumConfirmations(((BigInteger) val).intValue());
      }
      else if (key.equals("suggested_confirmations_threshold")) {
        if (tx.getInTxPool()) tx.setNumSuggestedConfirmations(((BigInteger) val).intValue());
        else tx.setNumSuggestedConfirmations(null);
      }
      else if (key.equals("amount")) {
        if (transfer == null) transfer = (isOutgoing ? new MoneroOutgoingTransfer() : new MoneroIncomingTransfer()).setTx(tx);
        transfer.setAmount((BigInteger) val);
      }
      else if (key.equals("address")) {
        if (!isOutgoing) {
          if (transfer == null) transfer = new MoneroIncomingTransfer().setTx(tx);
          ((MoneroIncomingTransfer) transfer).setAddress((String) val);
        }
      }
      else if (key.equals("payment_id")) {
        if (!MoneroTxWallet.DEFAULT_PAYMENT_ID.equals(val)) tx.setPaymentId((String) val);  // default is undefined
      }
      else if (key.equals("subaddr_index")) assertTrue(rpcTx.containsKey("subaddr_indices")); // handled by subaddr_indices
      else if (key.equals("subaddr_indices")) {
        if (transfer == null) transfer = (isOutgoing ? new MoneroOutgoingTransfer() : new MoneroIncomingTransfer()).setTx(tx);
        List<Map<String, BigInteger>> rpcIndices = (List<Map<String, BigInteger>>) val;
        transfer.setAccountIndex(rpcIndices.get(0).get("major").intValue());
        if (isOutgoing) {
          List<Integer> subaddressIndices = new ArrayList<Integer>();
          for (Map<String, BigInteger> rpcIndex : rpcIndices) subaddressIndices.add(rpcIndex.get("minor").intValue());
          ((MoneroOutgoingTransfer) transfer).setSubaddressIndices(subaddressIndices);
        } else {
          assertEquals(1, rpcIndices.size());
          ((MoneroIncomingTransfer) transfer).setSubaddressIndex(rpcIndices.get(0).get("minor").intValue());
        }
      }
      else if (key.equals("destinations")) {
        assertTrue(isOutgoing);
        List<MoneroDestination> destinations = new ArrayList<MoneroDestination>();
        for (Map<String, Object> rpcDestination : (List<Map<String, Object>>) val) {
          MoneroDestination destination = new MoneroDestination();
          destinations.add(destination);
          for (String destinationKey : rpcDestination.keySet()) {
            if (destinationKey.equals("address")) destination.setAddress((String) rpcDestination.get(destinationKey));
            else if (destinationKey.equals("amount")) destination.setAmount((BigInteger) rpcDestination.get(destinationKey));
            else throw new MoneroException("Unrecognized transaction destination field: " + destinationKey);
          }
        }
        if (transfer == null) transfer = new MoneroOutgoingTransfer().setTx(tx);
        ((MoneroOutgoingTransfer) transfer).setDestinations(destinations);
      }
      else if (key.equals("multisig_txset") && val != null) {} // TODO: handle this with value
      else if (key.equals("unsigned_txset") && val != null) {} // TODO: handle this with value
      else LOGGER.warn("WARNING: ignoring unexpected transaction field: " + key + ": " + val);
    }
    
    // link block and tx
    if (header != null) tx.setBlock(new MoneroBlock(header).setTxs(tx));
    
    // initialize final fields
    if (transfer != null) {
      if (isOutgoing) {
        if (tx.getOutgoingTransfer() != null) tx.getOutgoingTransfer().merge(transfer);
        else tx.setOutgoingTransfer((MoneroOutgoingTransfer) transfer);
      } else {
        tx.setIncomingTransfers(new ArrayList<MoneroIncomingTransfer>(Arrays.asList((MoneroIncomingTransfer) transfer)));
      }
    }
    
    // return initialized transaction
    return tx;
  }
  
  /**
   * Decodes a "type" from monero-wallet-rpc to initialize type and state
   * fields in the given transaction.
   * 
   * TODO: these should be safe set
   * 
   * @param rpcType is the type to decode
   * @param tx is the transaction to decode known fields to
   * @return {boolean} true if the rpc type indicates outgoing xor incoming
   */
  private static boolean decodeRpcType(String rpcType, MoneroTxWallet tx) {
    boolean isOutgoing;
    if (rpcType.equals("in")) {
      isOutgoing = false;
      tx.setIsConfirmed(true);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsCoinbase(false);
    } else if (rpcType.equals("out")) {
      isOutgoing = true;
      tx.setIsConfirmed(true);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsCoinbase(false);
    } else if (rpcType.equals("pool")) {
      isOutgoing = false;
      tx.setIsConfirmed(false);
      tx.setInTxPool(true);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsCoinbase(false);  // TODO: but could it be?
    } else if (rpcType.equals("pending")) {
      isOutgoing = true;
      tx.setIsConfirmed(false);
      tx.setInTxPool(true);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsCoinbase(false);
    } else if (rpcType.equals("block")) {
      isOutgoing = false;
      tx.setIsConfirmed(true);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsCoinbase(true);
    } else if (rpcType.equals("failed")) {
      isOutgoing = true;
      tx.setIsConfirmed(false);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(true);
      tx.setIsCoinbase(false);
    } else {
      throw new MoneroException("Unrecognized transfer type: " + rpcType);
    }
    return isOutgoing;
  }
  
  private static void mergeTx(List<MoneroTxWallet> txs, MoneroTxWallet tx) {
    mergeTx(txs, tx, false);
  }
  
  /**
   * Merges a transaction into a unique set of transactions.
   * 
   * TODO monero-wallet-rpc: skipIfAbsent only necessary because incoming payments not returned
   * when sent from/to same account
   * 
   * @param txs are existing transactions to merge into
   * @param tx is the transaction to merge into the existing txs
   * @param skipIfAbsent specifies if the tx should not be added
   *        if it doesn't already exist.  Only necessasry to handle
   *        missing incoming payments from #4500. // TODO
   * @returns the merged tx
   */
  private static void mergeTx(List<MoneroTxWallet> txs, MoneroTxWallet tx, boolean skipIfAbsent) {
    assertNotNull(tx.getId());
    for (MoneroTxWallet aTx : txs) {
      
      // merge tx
      if (aTx.getId().equals(tx.getId())) {
        
        // merge blocks which only exist when confirmed
        if (aTx.getBlock() != null || tx.getBlock() != null) {
          if (aTx.getBlock() == null) aTx.setBlock(new MoneroBlock().setTxs(new ArrayList<MoneroTx>(Arrays.asList(aTx))).setHeight(tx.getHeight()));
          if (tx.getBlock() == null) tx.setBlock(new MoneroBlock().setTxs(new ArrayList<MoneroTx>(Arrays.asList(tx))).setHeight(aTx.getHeight()));
          aTx.getBlock().merge(tx.getBlock());
        } else {
          aTx.merge(tx);
        }
        return;
      }
      
      // merge common block of different txs
      if (tx.getHeight() != null && tx.getHeight().equals(aTx.getHeight())) {
        aTx.getBlock().merge(tx.getBlock());
        if (aTx.getIsConfirmed()) assertTrue(aTx.getBlock().getTxs().contains(aTx));
      }
    }
    
    // add tx if it doesn't already exist unless skipped
    if (!skipIfAbsent) {
      txs.add(tx);
    } else {
      LOGGER.warn("WARNING: tx does not already exist"); 
    }
  }
  
  @SuppressWarnings("unchecked")
  private static MoneroTxWallet convertRpcTxWalletWithVout(Map<String, Object> rpcVout) {
    
    // initialize tx
    MoneroTxWallet tx = new MoneroTxWallet();
    tx.setIsConfirmed(true);
    tx.setIsRelayed(true);
    tx.setIsFailed(false);
    
    // initialize vout
    MoneroOutputWallet vout = new MoneroOutputWallet().setTx(tx);
    for (String key : rpcVout.keySet()) {
      Object val = rpcVout.get(key);
      if (key.equals("amount")) vout.setAmount((BigInteger) val);
      else if (key.equals("spent")) vout.setIsSpent((Boolean) val);
      else if (key.equals("key_image")) vout.setKeyImage(new MoneroKeyImage((String) val));
      else if (key.equals("global_index")) vout.setIndex(((BigInteger) val).intValue());
      else if (key.equals("tx_hash")) tx.setId((String) val);
      else if (key.equals("unlocked")) vout.setIsUnlocked((Boolean) val);
      else if (key.equals("frozen")) vout.setIsFrozen((Boolean) val);
      else if (key.equals("subaddr_index")) {
        Map<String, BigInteger> rpcIndices = (Map<String, BigInteger>) val;
        vout.setAccountIndex(rpcIndices.get("major").intValue());
        vout.setSubaddressIndex(rpcIndices.get("minor").intValue());
      }
      else if (key.equals("block_height")) {
        long height = ((BigInteger) val).longValue();
        tx.setBlock(new MoneroBlock().setHeight(height).setTxs(tx));
      }
      else LOGGER.warn("WARNING: ignoring unexpected transaction field with vout: " + key + ": " + val);
    }
    
    // initialize tx with vout
    List<MoneroOutput> vouts = new ArrayList<MoneroOutput>();
    vouts.add((MoneroOutput) vout); // have to cast to extended type because Java paramaterized types do not recognize inheritance
    tx.setVouts(vouts);
    return tx;
  }
}
