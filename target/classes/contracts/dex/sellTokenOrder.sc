{
  // Constants
  //
  // SellerPk[ProveDlog]
  // TokenId[Coll[Byte]]
  // ErgPerTokenNum[BigInt]
  // ErgPerTokenDenom[BigInt]
  // FeePerTokenNum[BigInt]
  // FeePerTokenDenom[BigInt]

  // Registers:
  //
  // R4[Coll[Byte]]:  TokenId
  // R5[BigInt]:      ErgPerTokenNum
  // R6[BigInt]:      ErgPerTokenDenom
  // R7[BigInt]:      FeePerTokenNum
  // R8[BigInt]:      FeePerTokenDenom
  // R9[Coll[Byte]]:  SELF.id

  val sellerPk          = SellerPk
  val tokenId           = TokenId
  val ergPerTokenNum    = ErgPerTokenNum
  val ergPerTokenDenom  = ErgPerTokenDenom
  val feePerTokenNum    = FeePerTokenNum
  val feePerTokenDenom  = FeePerTokenDenom

  val maxNumBigInt      = bigInt("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF")
  val maxDenomBigInt    = bigInt("4B3B4CA85A86C47A098A224000000000")

  val zero              = 0
  val zeroLong          = zero.toLong
  val zeroBigInt        = zeroLong.toBigInt

  val one               = 1
  val oneLong           = one.toLong
  val oneBigInt         = oneLong.toBigInt

  val negOne            = -1
  val negOneLong        = negOne.toLong
  val negOneBigInt      = negOneLong.toBigInt

  val ten               = 10
  val tenBigInt         = ten.toBigInt

  val selfTokenAmount   = SELF.tokens(zero)._2

  sellerPk || 
  (
    ergPerTokenNum    <= maxNumBigInt    && ergPerTokenNum   > negOneBigInt  && 
    ergPerTokenDenom  <= maxDenomBigInt  && ergPerTokenDenom > zeroBigInt    && (ergPerTokenDenom == oneBigInt || ((ergPerTokenDenom % tenBigInt) == zergoBigInt)) &&
    feePerTokenNum    <= maxNumBigInt    && feePerTokenNum   > negOneBigInt  &&
    feePerTokenDenom  <= maxDenomBigInt  && feePerTokenDenom > zeroBigInt    && (feePerTokenDenom == oneBigInt || ((feePerTokenDenom % tenBigInt) == zergoBigInt)) &&
  {   

    val combineFractions = { (num1: BigInt, denom1: BigInt, num2: BigInt, denom2: BigInt) =>
      if(denom1 == denom2){
        ((num1 + num2), denom1)
      }else if(denom1 > denom2){
        val divisor = denom1 / denom2
        ((num1 + (num2 * divisor)), denom1)
      }else{
        val divisor = denom2 / denom1
        (((num1 * divisor) + num2), denom2)
      }
    }

    val subtractFractions = { (num1: BigInt, denom1: BigInt, num2: BigInt, denom2: BigInt) =>
      if(denom1 == denom2){
        ((num1 - num2), denom1)
      }else if(denom1 > denom2){
        val divisor = denom1 / denom2
        ((num1 - (num2 * divisor)), denom1)
      }else{
        val divisor = denom2 / denom1
        (((num1 * divisor) - num2), denom2)
      }
    }

    val calculateNanoErgFromTokens = { (tokens: Long, num: BigInt, denom: BigInt) =>
      val tokens_x_num = tokens.toBigInt * num
      val nanoErg = if(denom >= tenBigInt){
        val roundUpOneTenth = tokens_x_num + (denom / tenBigInt)
        (roundUpOneTenth / denom)
      } else{
        tokens_x_num
      }
      nanoErg.toLong
    }

    val calculateTokensFromNanoErg = { (nanoErg: Long, num: BigInt, denom: BigInt) =>
      ((nanoErg.toBigInt * denom) / num).toLong
    }

    

    // box with ERGs(mine) for sold tokens
    // check that such box is only one in outputs is later in the code
    val returnBoxes = OUTPUTS.filter { (box: Box) => 
      val referencesMe = box.R4[Coll[Byte]].isDefined && box.R4[Coll[Byte]].get == SELF.id
      val canSpend = box.propositionBytes == sellerPk.propBytes
      referencesMe && canSpend      
    }

    // check if this order should get the spread for a given counter order(height)
    val spreadIsMine = { (counterOrderBoxHeight: Int) => 
      // strictly greater since equality gives win in buy order contract
      // Denys: we have to decide who gets the spread if height is equal, which goes to buy order
      counterOrderBoxHeight > SELF.creationInfo._1 
    }

    val invalidSpread = (negOneBigInt, oneBigInt)
    val zeroSpread    = (zeroBigInt, oneBigInt)
    val buyOrderBoxesAreSortedBySpread = { (boxes: Coll[Box]) => 
      boxes.size > zero && {
        val topBox                  = boxes(zero)
        val topBoxHeight            = topBox.creationInfo._1
        val topBoxErgPerTokenNum    = topBox.R5[BigInt].getOrElse(zeroBigInt)
        val topBoxErgPerTokenDenom  = topBox.R6[BigInt].getOrElse(zeroBigInt)
        val topBoxSpreadFraction    = if(boxErgPerTokenNum > zeroBigInt && boxErgPerTokenDenom > zeroBigInt){
          subtractFractions(topBoxErgPerTokenNum, topBoxErgPerTokenDenom, ergPerTokenNum, ergPerTokenDenom)
        } else {
          invalidSpread
        }

        if (spreadIsMine(topBoxHeight) && topBoxSpreadFraction._1 > negOneBigInt) {   
          
          boxes.fold(topBoxSpreadFraction, { (t: (BigInt, BigInt), b: Box) => 
            val prevSpreadNum   = t._1
            val prevSpreadDenom = t._2

            if(prevSpreadNum > negOneBigInt){  
              val boxErgPerTokenNum   = b.R5[BigInt].getOrElse(zeroBigInt)
              val boxErgPerTokenDenom = b.R6[BigInt].getOrElse(zeroBigInt)
              val isSpreadMine        = spreadIsMine(b.creationInfo._1)

              val spreadFraction = if(boxErgPerTokenNum > zeroBigInt && boxErgPerTokenDenom > zeroBigInt){
                subtractFractions(boxErgPerTokenNum, boxErgPerTokenDenom, ergPerTokenNum, ergPerTokenDenom)
              } else {
                invalidSpread
              }

              if(spreadFraction._1 > negOneBigInt) {
                if(isSpreadMine){
                  val prevSpreadLessSpread = subtractFractions(prevSpreadNum, prevSpreadDenom, spreadFraction._1, spreadFraction._2)

                  if(prevSpreadLessSpread._1 > negOneBigInt){
                      spreadFraction
                  } else {
                    invalidSpread
                  }
                } else {
                  zeroSpread
                }
              } else {
                invalidSpread
              }
            } else {
              invalidSpread
            }
          })._1 > negOneBigInt
        } else {
          false
        }
      }
    }



    // counter(buy) orders that are matched against this order
    val spendingBuyOrders = INPUTS.filter { (box: Box) =>
      val isR4 = box.R4[Coll[Byte]].isDefined && box.R4[Coll[Byte]].get == tokenId
      val isR5 = box.R5[BigInt].isDefined
      val isR6 = box.R6[BigInt].isDefined
      val isR7 = box.R7[BigInt].isDefined
      val isR8 = box.R8[BigInt].isDefined
      val isZeroBoxTokens = box.tokens.size == zero
      
      isZeroBoxTokens && isR4 && isR5 && isR6 && isR7 && isR8
    }

 
    returnBoxes.size == one && 
      spendingBuyOrders.size > zero && 
      buyOrderBoxesAreSortedBySpread(spendingBuyOrders) && {

      val returnBox = returnBoxes(zero)

      // in case of partial matching new sell order box should be created with tokens that are not matched in this tx
      // check that there is only one such box is made later in the code
      val foundResidualOrderBoxes = OUTPUTS.filter { (box: Box) => 
        val boxErgPerTokenNum   = box.R5[BigInt].getOrElse(zeroBigInt)
        val boxErgPerTokenDenom = box.R6[BigInt].getOrElse(zeroBigInt)
        val boxFeePerTokenNum   = box.R7[BigInt].getOrElse(zeroBigInt)
        val boxFeePerTokenDenom = box.R8[BigInt].getOrElse(zeroBigInt)

        val isTokenId                 = box.R4[Coll[Byte]].isDefined  && box.R4[Coll[Byte]].get == tokenId 
        val isErgPerTokenNum          = boxErgPerTokenNum             == ergPerTokenNum
        val isErgPerTokenDenom        = boxErgPerTokenDenom           == ergPerTokenDenom
        val isFeePerTokenNum          = boxFeePerTokenNum             == feePerTokenNum
        val isFeePerTokenDenom        = boxFeePerTokenDenom           == feePerTokenDenom
        val contractParamsAreCorrect  = isTokenId && isErgPerTokenNum && isErgPerTokenDenom     && isFeePerTokenNum && isFeePerTokenDenom
        val referenceMe               = box.R9[Coll[Byte]].isDefined  && box.R9[Coll[Byte]].get == SELF.id 
        val guardedByTheSameContract  = box.propositionBytes          == SELF.propositionBytes
        
        contractParamsAreCorrect && referenceMe && guardedByTheSameContract
      }

      // aggregated spread we get from all counter(buy) orders
      val fullSpread = { (tokenAmount: Long) =>
        spendingBuyOrders.fold((tokenAmount, zeroLong), { (t: (Long, Long), buyOrder: Box) => 
          val returnTokensLeft      = t._1
          val accumulatedFullSpread = t._2

          val buyOrderErgPerTokenNum    = buyOrder.R5[BigInt].get
          val buyOrderErgPerTokenDenom  = buyOrder.R6[BigInt].get
          val buyOrderFeePerTokenNum    = buyOrder.R7[BigInt].get
          val buyOrderFeePerTokenDenom  = buyOrder.R8[BigInt].get
          val buyOrderNanoErg           = buyOrder.value

          val combinedPriceFee = combineFractions(buyOrderErgPerTokenNum, buyOrderErgPerTokenDenom, buyOrderFeePerTokenNum, buyOrderFeePerTokenDenom)

          val buyOrderTokenAmountCapacity = calculateTokensFromNanoErg(buyOrderNanoErg, combinedPriceFee._1, combinedPriceFee._2) 
          val tokenAmountInThisOrder = min(returnTokensLeft, buyOrderTokenAmountCapacity)
          if (spreadIsMine(box.creationInfo._1)) {
            // spread is ours
            val spreadPerToken = subtractFractions(buyOrderErgPerTokenNum, buyOrderErgPerTokenDenom, ergPerTokenNum, ergPerTokenDenom)

            val buyOrderSpread = calculateNanoErgFromTokens(tokenAmountInThisOrder, spreadPerToken._1, spreadPerToken._2)
            (returnTokensLeft - tokenAmountInThisOrder, accumulatedFullSpread + buyOrderSpread)
          } else {
            // spread is not ours
            (returnTokensLeft - tokenAmountInThisOrder, accumulatedFullSpread)
          }
        })._2
      }

      // branch for total matching (all tokens are sold and full amount ERGs received)
      val totalNanoErgCost  = calculateNanoErgFromTokens(selfTokenAmount, ergPerTokenNum, ergPerTokenDenom)
      val totalMatching     = (returnBox.value == totalNanoErgCost + fullSpread(selfTokenAmount))

      // branch for partial matching, e.g. besides received ERGs we demand a new buy order with tokens for 
      // non-matched part of this order
      val partialMatching = {
        foundResidualOrderBoxes.size == one && {
          val residualOrderBox = foundResidualOrderBoxes(zero)
          val residualOrderTokenData = residualOrderBox.tokens(zero)
          val residualOrderTokenAmount = residualOrderTokenData._2
          val soldTokenAmount = selfTokenAmount - residualOrderTokenAmount
          val soldTokenErgValue = calculateNanoErgFromTokens(soldTokenAmount, ergPerTokenNum, ergPerTokenDenom)
          val expectedDexFee = calculateNanoErgFromTokens(soldTokenAmount, feePerTokenNum, feePerTokenDenom)

          val residualOrderTokenId = residualOrderTokenData._1
          val tokenIdIsCorrect = residualOrderTokenId == tokenId

          val residualOrderValueIsCorrect = residualOrderBox.value == (SELF.value - expectedDexFee)
          val returnBoxValueIsCorrect = returnBox.value == soldTokenErgValue + fullSpread(soldTokenAmount)
          tokenIdIsCorrect && 
            soldTokenAmount >= one && 
            residualOrderValueIsCorrect && 
            returnBoxValueIsCorrect
        }
      }

      (totalMatching || partialMatching) 
    }

  })
}