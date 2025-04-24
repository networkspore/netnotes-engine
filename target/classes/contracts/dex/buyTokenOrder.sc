{
  // Constants
  //
  // BuyerPk[ProveDlog]
  // TokenId[Coll[Byte]]
  // ErgPerTokenNum[String]
  // ErgPerTokenDenom[String]
  // FeePerTokenNum[String]
  // FeePerTokenDenom[String]

  // Registers:
  //
  // R4[Coll[Byte]]:  TokenId
  // R5[String]:      ErgPerTokenNum
  // R6[String]:      ErgPerTokenDenom
  // R7[String]:      FeePerTokenNum
  // R8[String]:      FeePerTokenDenom
  // R9[Coll[Byte]]   SELF.id

  val buyterPk          = BuyterPk
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
  val selfNanoErg       = SELF.value

  val combineFractions = { (num1:BigInt, denom1:BigInt, num2:BigInt, denom2:BigInt) =>
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

  val subtractFractions = { (num1:BigInt, denom1:BigInt, num2:BigInt, denom2:BigInt) =>
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

  val calculateNanoErgFromTokens = { (tokens:Long, num:BigInt, denom:BigInt) =>
    val tokens_x_num = tokens.toBigInt * num
    val nanoErg = if(denom >= tenBigInt && tokens > zeroLong){
      val roundUpOneTenth = tokens_x_num + (denom / tenBigInt)
      (roundUpOneTenth / denom)
    } else{
      tokens_x_num
    }
    nanoErg.toLong
  }

  val calculateTokensFromNanoErg = { (nanoErg:Long, num:BigInt, denom:BigInt) =>
    if(nanoErg > zeroLong){
      ((nanoErg.toBigInt * denom) / num).toLong
    } else {
      zeroLong
    }
  }


  val requestedTokenAmount = calculateTokensFromNanoErg(selfNanoErg, ergPerTokenNum, ergPerTokenDenom)

  buyerPk || 
  (
    ergPerTokenNum    <= maxNumBigInt    && ergPerTokenNum   > negOneBigInt  && 
    ergPerTokenDenom  <= maxDenomBigInt  && ergPerTokenDenom > zeroBigInt    && (ergPerTokenDenom == oneBigInt || ((ergPerTokenDenom % tenBigInt) == zergoBigInt)) &&
    feePerTokenNum    <= maxNumBigInt    && feePerTokenNum   > negOneBigInt  &&
    feePerTokenDenom  <= maxDenomBigInt  && feePerTokenDenom > zeroBigInt    && (feePerTokenDenom == oneBigInt || ((feePerTokenDenom % tenBigInt) == zergoBigInt)) &&
  {

    // counter(sell) orders that are matched against this order
    val spendingSellOrders = INPUTS.filter { (b: Box) =>

      val isR4 = b.R4[Coll[Byte]].isDefined && b.R4[Coll[Byte]].get == tokenId 
      val isR5 = b.R5[BigInt].isDefined
      val isR6 = b.R6[BigInt].isDefined
      val isR7 = b.R7[BigInt].isDefined
      val isR8 = b.R8[BigInt].isDefined
      
      val isTokens = b.tokens.size == one && b.tokens(zero)._1 == tokenId

      isTokens && isR4 && isR5 && isR6 && isR7 && isR8
    }

    // box with mine(bought) tokens
    // check that such box is only one in outputs is later in the code
    val returnBoxes = OUTPUTS.filter { (b: Box) => 
      val referencesMe = b.R4[Coll[Byte]].isDefined && b.R4[Coll[Byte]].get == SELF.id 
      val canSpend = b.propositionBytes == buyerPk.propBytes
      referencesMe && canSpend      
    }

    // check if this order should get the spread for a given counter order(height)
    val spreadIsMine = { (counterOrderBoxHeight: Int) => 
      // greater or equal since only a strict greater gives win in sell order contract
      // Denys: if height is equal buy order gets the spread
      counterOrderBoxHeight >= SELF.creationInfo._1 
    }

    val invalidSpread = (negOneBigInt, oneBigInt)
    val zeroSpread    = (zeroBigInt, oneBigInt)
    // check that counter(sell) orders are sorted by spread in INPUTS
    // so that the bigger(top) spread will be "consumed" first
    val sellOrderBoxesAreSortedBySpread = { (boxes: Coll[Box]) => 
      boxes.size > zero && {
        val topBox                  = boxes(zero)
        val topBoxHeight            = topBox.creationInfo._1
        val topBoxErgPerTokenNum    = topBox.R5[BigInt].getOrElse(zeroBigInt)
        val topBoxErgPerTokenDenom  = topBox.R6[BigInt].getOrElse(oneBigInt)

        val topBoxSpreadFraction = if(topBoxErgPerTokenNum > zeroBigInt && topBoxErgPerTokenDenom > zeroBigInt) {
          subtractFractions(ergPerTokenNum, ergPerTokenDenom, topBoxErgPerTokenNum, topBoxErgPerTokenDenom)
        } else{
          invalidSpread
        }

        if(spreadIsMine(topBoxHeight) && topBoxSpreadFraction._1 > negOneBigInt){
          boxes.fold(topBoxSpreadFraction, { (t: (BigInt, BigInt), b: Box) => 
            val prevSpreadNum   = t._1
            val prevSpreadDenom = t._2
  
            if(prevSpreadNum > negOneBigInt){  
              val boxErgPerTokenNum   = b.R5[BigInt].get
              val boxErgPerTokenDenom = b.R6[BigInt].get
              val isSpreadMine        = spreadIsMine(b.creationInfo._1)

              val spreadFraction = subtractFractions(ergPerTokenNum, ergPerTokenDenom, boxErgPerTokenNum, boxErgPerTokenDenom)
            
              val prevSpreadLessSpread = if(spreadFraction._1 > negOneBigInt) {
                  if(isSpreadMine){
                    subtractFractions(prevSpreadNum, prevSpreadDenom, spreadFraction._1, spreadFraction._2)
                  } else {
                    (zeroBigInt, negOneBigInt)
                  }
              } else {
                invalidSpread
              }

              if(prevSpreadLessSpread._1 > negOneBigInt){
                if(isSpreadMine){
                  spreadFraction
                } else{
                  (zeroBigInt, oneBigInt)
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


    returnBoxes.size == 1 && 
      spendingSellOrders.size > zero && 
      sellOrderBoxesAreSortedBySpread(spendingSellOrders) && {

      val returnBox = returnBoxes(zero)
      // token amount that are bought
      val boughtTokenAmount = if (returnBox.tokens.size == one) {
        returnBox.tokens(zero)._2
      } else {
        zeroLong
      }
      
      // DEX fee that we allow for matcher to take
      val expectedDexFee = if(boughtTokenAmount > zeroLong) {
        calculateNanoErgFromTokens(boughtTokenAmount, feePerTokenNum, feePerTokenDenom)
      } else {
        zeroLong
      }

    
      
      // in case of partial matching new buy order box should be created with funds that are not matched in this tx
      // check that there is only one such box is made later in the code
      val foundResidualOrderBoxes = OUTPUTS.filter { (b: Box) => 

        val tokenIdParamIsCorrect     = b.R4[Coll[Byte]].isDefined && b.R4[Coll[Byte]].get == tokenId 
        val boxErgPerTokenNum         = b.R5[BigInt].getOrElse(zeroBigInt)
        val boxErgPerTokenDenom       = b.R6[BigInt].getOrElse(oneBigInt)
        val boxFeePerTokenNum         = b.R7[BigInt].getOrElse(zeroBigInt)
        val boxFeePerTokenDenom       = b.R8[BigInt].getOrElse(oneBigInt)
        val referenceMe               = b.R9[Coll[Byte]].isDefined && b.R9[Coll[Byte]].get == SELF.id 
        val guardedByTheSameContract  = b.propositionBytes == SELF.propositionBytes

        val tokenPriceParamIsCorrect      = boxErgPerTokenNum == ergPerTokenNum && boxErgPerTokenDenom == ergPerTokenDenom
        val dexFeePerTokenParamIsCorrect  = boxFeePerTokenNum == feePerTokenNum && boxFeePerTokenDenom == feePerTokenDenom
        val contractParamsAreCorrect      = tokenIdParamIsCorrect && tokenPriceParamIsCorrect && dexFeePerTokenParamIsCorrect
        
        contractParamsAreCorrect && referenceMe && guardedByTheSameContract
      }

      
      // aggregated spread we get from all counter(sell) orders
      val fullSpread = {
        spendingSellOrders.fold((returnTokenAmount, zeroLong), { (t: (Long, Long), sellOrder: Box) => 
          val returnTokensLeft          = t._1
          val accumulatedFullSpread     = t._2
          val sellOrderTokenAmount      = sellOrder.tokens(zero)._2
          val sellOrderHeight           = sellOrder.creationInfo._1
          val sellOrderTokenPriceNum    = sellOrder.R5[BigInt].get
          val sellOrderTokenPriceDenom  = sellOrder.R6[BigInt].get
          
          val tokenAmountFromThisOrder  = min(returnTokensLeft, sellOrderTokenAmount)
          
          if (spreadIsMine(sellOrderHeight)) {
            // spread is ours
            val spreadPerTokenFraction = subtractFractions( ergPerTokenNum, ergPerTokenDenom, sellOrderTokenPriceNum, sellOrderTokenPriceDenom)
            val sellOrderSpread = calculateNanoErgFromTokens(tokenAmountFromThisOrder, spreadPerTokenFraction._1, spreadPerTokenFraction._2)
          
            (returnTokensLeft - tokenAmountFromThisOrder, accumulatedFullSpread + sellOrderSpread)
          } else {
            // spread is not ours
            (returnTokensLeft - tokenAmountFromThisOrder, accumulatedFullSpread)
          }
        })._2
      }

      // ERGs paid for the bought tokens
      val returnTokenValue  = calculateNanoErgFromTokens(returnTokenAmount, ergPerTokenNum, ergPerTokenDenom)
      // branch for total matching (all ERGs are spent and correct amount of tokens is bought)
      val totalMatching     = ((SELF.value - expectedDexFee) == returnTokenValue) && (returnBox.value >= fullSpread)
      
      // branch for partial matching, e.g. besides bought tokens we demand a new buy order with ERGs for 
      // non-matched part of this order
      val partialMatching = {
        val correctResidualOrderBoxValue = (SELF.value - returnTokenValue - expectedDexFee)
        foundResidualOrderBoxes.size == one && 
          foundResidualOrderBoxes(zero).value == correctResidualOrderBoxValue && 
          returnBox.value >= fullSpread
      }

      val coinsSecured = partialMatching || totalMatching

      val tokenIdIsCorrect = returnBox.tokens.size == one && returnBox.tokens(zero)._1 == tokenId
      
      allOf(Coll(
          tokenIdIsCorrect,
          boughtTokenAmount >= 1,
          coinsSecured
      ))
    }
  })
}
      