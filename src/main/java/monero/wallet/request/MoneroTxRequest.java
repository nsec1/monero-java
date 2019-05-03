package monero.wallet.request;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import common.types.Filter;
import common.utils.GenUtils;
import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroOutput;
import monero.wallet.model.MoneroIncomingTransfer;
import monero.wallet.model.MoneroOutgoingTransfer;
import monero.wallet.model.MoneroTransfer;
import monero.wallet.model.MoneroTxWallet;

/**
 * Filters transactions that don't match initialized filter criteria.
 */
public class MoneroTxRequest extends MoneroTxWallet implements Filter<MoneroTxWallet> {
  
  private Boolean isOutgoing;
  private Boolean isIncoming;
  private Collection<String> txIds;
  private Boolean hasPaymentId;
  private Collection<String> paymentIds;
  private Integer minHeight;
  private Integer maxHeight;
  private Boolean includeOutputs;
  private MoneroTransferRequest transferRequest;
  
  public Boolean getIsOutgoing() {
    return isOutgoing;
  }

  public MoneroTxRequest setIsOutgoing(Boolean isOutgoing) {
    this.isOutgoing = isOutgoing;
    return this;
  }

  public Boolean getIsIncoming() {
    return isIncoming;
  }

  public MoneroTxRequest setIsIncoming(Boolean isIncoming) {
    this.isIncoming = isIncoming;
    return this;
  }

  public Collection<String> getTxIds() {
    return txIds;
  }

  public MoneroTxRequest setTxIds(Collection<String> txIds) {
    this.txIds = txIds;
    return this;
  }
  
  public MoneroTxRequest setTxIds(String... txIds) {
    this.txIds = GenUtils.arrayToList(txIds);
    return this;
  }
  
  public MoneroTxRequest setTxId(String txId) {
    return setTxIds(Arrays.asList(txId));
  }

  public Boolean getHasPaymentId() {
    return hasPaymentId;
  }

  public MoneroTxRequest setHasPaymentId(Boolean hasPaymentId) {
    this.hasPaymentId = hasPaymentId;
    return this;
  }

  public Collection<String> getPaymentIds() {
    return paymentIds;
  }

  public MoneroTxRequest setPaymentIds(Collection<String> paymentIds) {
    this.paymentIds = paymentIds;
    return this;
  }
  
  public MoneroTxRequest setPaymentId(String paymentId) {
    return setPaymentIds(Arrays.asList(paymentId));
  }

  public Integer getMinHeight() {
    return minHeight;
  }

  public MoneroTxRequest setMinHeight(Integer minHeight) {
    this.minHeight = minHeight;
    return this;
  }

  public Integer getMaxHeight() {
    return maxHeight;
  }

  public MoneroTxRequest setMaxHeight(Integer maxHeight) {
    this.maxHeight = maxHeight;
    return this;
  }

  public Boolean getIncludeOutputs() {
    return includeOutputs;
  }

  public MoneroTxRequest setIncludeOutputs(Boolean includeOutputs) {
    this.includeOutputs = includeOutputs;
    return this;
  }

  public MoneroTransferRequest getTransferRequest() {
    return transferRequest;
  }

  public MoneroTxRequest setTransferRequest(MoneroTransferRequest transferRequest) {
    this.transferRequest = transferRequest;
    return this;
  }
  
  @Override
  public boolean meetsCriteria(MoneroTxWallet tx) {
    if (tx == null) return false;
    
    // filter on tx
    if (this.getId() != null && !this.getId().equals(tx.getId())) return false;
    if (this.getPaymentId() != null && !this.getPaymentId().equals(tx.getPaymentId())) return false;
    if (this.getIsConfirmed() != null && this.getIsConfirmed() != tx.getIsConfirmed()) return false;
    if (this.getInTxPool() != null && this.getInTxPool() != tx.getInTxPool()) return false;
    if (this.getDoNotRelay() != null && this.getDoNotRelay() != tx.getDoNotRelay()) return false;
    if (this.getIsRelayed() != null && this.getIsRelayed() != tx.getIsRelayed()) return false;
    if (this.getIsFailed() != null && this.getIsFailed() != tx.getIsFailed()) return false;
    if (this.getIsCoinbase() != null && this.getIsCoinbase() != tx.getIsCoinbase()) return false;
    
    // at least one transfer must meet transfer request if defined
    if (this.getTransferRequest() != null) {
      boolean matchFound = false;
      if (tx.getOutgoingTransfer() != null && this.getTransferRequest().meetsCriteria(tx.getOutgoingTransfer())) matchFound = true;
      else if (tx.getIncomingTransfers() != null) {
        for (MoneroTransfer incomingTransfer : tx.getIncomingTransfers()) {
          if (this.getTransferRequest().meetsCriteria(incomingTransfer)) {
            matchFound = true;
            break;
          }
        }
      }
      if (!matchFound) return false;
    }
    
    // filter on having a payment id
    if (this.getHasPaymentId() != null) {
      if (this.getHasPaymentId() && tx.getPaymentId() == null) return false;
      if (!this.getHasPaymentId() && tx.getPaymentId() != null) return false;
    }
    
    // filter on incoming
    if (this.getIsIncoming() != null) {
      if (this.getIsIncoming() && !tx.getIsIncoming()) return false;
      if (!this.getIsIncoming() && tx.getIsIncoming()) return false;
    }
    
    // filter on outgoing
    if (this.getIsOutgoing() != null) {
      if (this.getIsOutgoing() && !tx.getIsOutgoing()) return false;
      if (!this.getIsOutgoing() && tx.getIsOutgoing()) return false;
    }
    
    // filter on remaining fields
    Integer height = tx.getBlock() == null ? null : tx.getBlock().getHeight();
    if (this.getTxIds() != null && !this.getTxIds().contains(tx.getId())) return false;
    if (this.getPaymentIds() != null && !this.getPaymentIds().contains(tx.getPaymentId())) return false;
    if (this.getHeight() != null && height != this.getHeight()) return false;
    if (this.getMinHeight() != null && (height == null || height < this.getMinHeight())) return false;
    if (this.getMaxHeight() != null && (height == null || height > this.getMaxHeight())) return false;
    
    // transaction meets request criteria
    return true;
  }
  
  @Override
  public String toString() {
    throw new RuntimeException("Not implemented");
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public MoneroTxRequest setIncomingTransfers(List<MoneroIncomingTransfer> incomingTransfers) {
    super.setIncomingTransfers(incomingTransfers);
    return this;
  }

  @Override
  public MoneroTxRequest setOutgoingTransfer(MoneroOutgoingTransfer outgoingTransfer) {
    super.setOutgoingTransfer(outgoingTransfer);
    return this;
  }

  @Override
  public MoneroTxRequest setVouts(List<MoneroOutput> vouts) {
    super.setVouts(vouts);
    return this;
  }

  @Override
  public MoneroTxRequest setNote(String note) {
    super.setNote(note);
    return this;
  }

  @Override
  public MoneroTxRequest setBlock(MoneroBlock block) {
    super.setBlock(block);
    return this;
  }

  @Override
  public MoneroTxRequest setId(String id) {
    super.setId(id);
    return this;
  }

  @Override
  public MoneroTxRequest setVersion(Integer version) {
    super.setVersion(version);
    return this;
  }

  @Override
  public MoneroTxRequest setIsCoinbase(Boolean isCoinbase) {
    super.setIsCoinbase(isCoinbase);
    return this;
  }

  @Override
  public MoneroTxRequest setFee(BigInteger fee) {
    super.setFee(fee);
    return this;
  }

  @Override
  public MoneroTxRequest setMixin(Integer mixin) {
    super.setMixin(mixin);
    return this;
  }

  @Override
  public MoneroTxRequest setDoNotRelay(Boolean doNotRelay) {
    super.setDoNotRelay(doNotRelay);
    return this;
  }

  @Override
  public MoneroTxRequest setIsRelayed(Boolean isRelayed) {
    super.setIsRelayed(isRelayed);
    return this;
  }

  @Override
  public MoneroTxRequest setIsConfirmed(Boolean isConfirmed) {
    super.setIsConfirmed(isConfirmed);
    return this;
  }

  @Override
  public MoneroTxRequest setInTxPool(Boolean inTxPool) {
    super.setInTxPool(inTxPool);
    return this;
  }

  @Override
  public MoneroTxRequest setNumConfirmations(Integer numConfirmations) {
    super.setNumConfirmations(numConfirmations);
    return this;
  }

  @Override
  public MoneroTxRequest setNumSuggestedConfirmations(Integer numSuggestedConfirmations) {
    super.setNumSuggestedConfirmations(numSuggestedConfirmations);
    return this;
  }

  @Override
  public MoneroTxRequest setUnlockTime(Integer unlockTime) {
    super.setUnlockTime(unlockTime);
    return this;
  }

  @Override
  public MoneroTxRequest setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    super.setLastRelayedTimestamp(lastRelayedTimestamp);
    return this;
  }

  @Override
  public MoneroTxRequest setReceivedTimestamp(Long receivedTimestamp) {
    super.setReceivedTimestamp(receivedTimestamp);
    return this;
  }

  @Override
  public MoneroTxRequest setIsDoubleSpend(Boolean isDoubleSpend) {
    super.setIsDoubleSpend(isDoubleSpend);
    return this;
  }

  @Override
  public MoneroTxRequest setKey(String key) {
    super.setKey(key);
    return this;
  }

  @Override
  public MoneroTxRequest setFullHex(String hex) {
    super.setFullHex(hex);
    return this;
  }

  @Override
  public MoneroTxRequest setPrunedHex(String prunedHex) {
    super.setPrunedHex(prunedHex);
    return this;
  }

  @Override
  public MoneroTxRequest setPrunableHex(String prunableHex) {
    super.setPrunableHex(prunableHex);
    return this;
  }

  @Override
  public MoneroTxRequest setPrunableHash(String prunableHash) {
    super.setPrunableHash(prunableHash);
    return this;
  }

  @Override
  public MoneroTxRequest setSize(Integer size) {
    super.setSize(size);
    return this;
  }

  @Override
  public MoneroTxRequest setWeight(Integer weight) {
    super.setWeight(weight);
    return this;
  }

  @Override
  public MoneroTxRequest setVins(List<MoneroOutput> vins) {
    super.setVins(vins);
    return this;
  }

  @Override
  public MoneroTxRequest setOutputIndices(List<Integer> outputIndices) {
    super.setOutputIndices(outputIndices);
    return this;
  }

  @Override
  public MoneroTxRequest setMetadata(String metadata) {
    super.setMetadata(metadata);
    return this;
  }

  @Override
  public MoneroTxRequest setCommonTxSets(String commonTxSets) {
    super.setCommonTxSets(commonTxSets);
    return this;
  }

  @Override
  public MoneroTxRequest setExtra(int[] extra) {
    super.setExtra(extra);
    return this;
  }

  @Override
  public MoneroTxRequest setRctSignatures(Object rctSignatures) {
    super.setRctSignatures(rctSignatures);
    return this;
  }

  @Override
  public MoneroTxRequest setRctSigPrunable(Object rctSigPrunable) {
    super.setRctSigPrunable(rctSigPrunable);
    return this;
  }

  @Override
  public MoneroTxRequest setIsKeptByBlock(Boolean isKeptByBlock) {
    super.setIsKeptByBlock(isKeptByBlock);
    return this;
  }

  @Override
  public MoneroTxRequest setIsFailed(Boolean isFailed) {
    super.setIsFailed(isFailed);
    return this;
  }

  @Override
  public MoneroTxRequest setLastFailedHeight(Integer lastFailedHeight) {
    super.setLastFailedHeight(lastFailedHeight);
    return this;
  }

  @Override
  public MoneroTxRequest setLastFailedId(String lastFailedId) {
    super.setLastFailedId(lastFailedId);
    return this;
  }

  @Override
  public MoneroTxRequest setMaxUsedBlockHeight(Integer maxUsedBlockHeight) {
    super.setMaxUsedBlockHeight(maxUsedBlockHeight);
    return this;
  }

  @Override
  public MoneroTxRequest setMaxUsedBlockId(String maxUsedBlockId) {
    super.setMaxUsedBlockId(maxUsedBlockId);
    return this;
  }

  @Override
  public MoneroTxRequest setSignatures(List<String> signatures) {
    super.setSignatures(signatures);
    return this;
  }
}