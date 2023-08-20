package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.dto.TransactionMapper;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionGroup;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WalletTransactionService {

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionGroupRepository transactionGroupRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public UUID transferBetweenTwoWallets(Integer senderId, Integer recipientId, Long amount) throws Exception {
        // 1. Create a TransactionGroup with the status IN_PROGRESS
        TransactionGroup transactionGroup = new TransactionGroup();
        transactionGroup.setStatus(TransactionGroupStatus.IN_PROGRESS);
        transactionGroup = transactionGroupRepository.save(transactionGroup);

        UUID referenceId = transactionGroup.getId();  // This is the UUID generated

        try {
            // 2. Hold the amount from the sender's account
            walletService.hold(senderId, amount, referenceId); // hold for deduction
            walletService.reserve(recipientId, amount, referenceId); // hold for addition

            // 3. Confirm the transaction for the recipient
            walletService.confirm(recipientId, referenceId);

            // 4. Confirm the hold on the sender's side and hold the amount for the recipient
            walletService.confirm(senderId, referenceId);

            // 5. Update the TransactionGroup status to CONFIRMED
            transactionGroup.setStatus(TransactionGroupStatus.CONFIRMED);
            transactionGroupRepository.save(transactionGroup);
        } catch (Exception e) {
            // If any step fails, revert the previous steps using the referenceId

            // Reject the transaction for the sender
            walletService.reject(senderId, referenceId);

            // Reject the transaction for the recipient (if needed)
            walletService.reject(recipientId, referenceId);

            // Update the TransactionGroup status to REJECTED
            transactionGroup.setStatus(TransactionGroupStatus.REJECTED);
            transactionGroupRepository.save(transactionGroup);

            throw e;  // Propagate the exception for further handling or to inform the user
        }

        return referenceId; // Return the referenceId for tracking or further operations
    }

//    @Transactional(Transactional.TxType.NOT_SUPPORTED)
//    public UUID transferWithTransactionFee(Integer senderWalletId, Integer recipientWalletId, Integer feeWalletId, Long amount, Long feeAmount) throws InsufficientFundsException, WalletNotFoundException, TransactionNotFoundException, WalletNotFoundException, InsufficientFundsException {
//        // Create a new transaction group
//        TransactionGroup transactionGroup = new TransactionGroup();
//        transactionGroup.setStatus(TransactionGroupStatus.IN_PROGRESS);
//        transactionGroup = transactionGroupRepository.save(transactionGroup);
//
//        UUID referenceId = transactionGroup.getId();  // This is the UUID generated
//
//        try {
//            // 1. Hold the total amount from the sender's wallet
//            walletService.hold(senderWalletId, amount + feeAmount, referenceId);
//
//            // 2. Confirm the specified amount to the recipient
//            walletService.confirm(recipientWalletId, referenceId);
//
//            // 3. Hold the fee amount from the sender's wallet (this could be combined with the first step but separating it for clarity)
//            walletService.hold(senderWalletId, feeAmount, referenceId);
//
//            // 4. Confirm the fee amount to the fee wallet
//            walletService.confirm(feeWalletId, referenceId);
//
//            // Update the transaction group status to CONFIRMED
//            transactionGroup.setStatus(TransactionGroupStatus.CONFIRMED);
//            transactionGroupRepository.save(transactionGroup);
//
//        } catch (Exception e) {
//            // If any part of the transaction fails, the hold amount on the sender's wallet should be rejected
//            walletService.reject(senderWalletId, referenceId);
//
//            // Update the transaction group status to REJECTED
//            transactionGroup.setStatus(TransactionGroupStatus.REJECTED);
//            transactionGroupRepository.save(transactionGroup);
//
//            throw e; // Optionally, re-throw the exception for further handling outside this method
//        }
//
//        return referenceId; // Return the reference ID for tracking
//    }

    public TransactionGroupStatus getStatusForReferenceId(UUID referenceId) {
        return transactionGroupRepository.findById(referenceId)
                .map(TransactionGroup::getStatus)
                .orElseThrow(() -> new EntityNotFoundException("No transaction group found with referenceId: " + referenceId));
    }

    public List<TransactionDTO> getTransactionsByReferenceId(UUID referenceId) {
        List<Transaction> transactions = transactionRepository.findByReferenceId(referenceId);
        return TransactionMapper.INSTANCE.toDTOList(transactions);
    }
}
