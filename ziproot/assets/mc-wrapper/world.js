/**
 * mc-rtm-wrapper
 * RTMの各種スクリプト向けマインクラフトのラッパー。
 * 1.7.10, 1.12.2両対応。
 *
 * MIT Licenseの日本語要約
 * 下記のライセンス全文を保持してる限り(それ以外の)いかなる制限なくこのスクリプトを扱う事ができる。
 * 原作者(私, anatawa12)はいかなる損害の保証はせず、原作者が各種責任を追わないものとする。
 *
 * MIT Licenseの日本語要約の補足
 * ここのブロックコメントはライセンス表記に含まないので削除していただいて問題ありません。
 * 次のブロックコメントがライセンス表記に当たります。
 */
/**
 * MIT License
 *
 * Copyright (c) 2020 anatawa12(翳河翔)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

if (__rtm_mc_wrapper__ == null) throw new Error("you have to load(include) common.js of mc-wrapper before include other mc-wrapper scripts");

rmw.includeGuard("mc-wrapper:world", ["mc-wrapper:common", "mc-wrapper:block"], function (global) {
    // common import
    var NGTLog = Packages.jp.ngt.ngtlib.io.NGTLog

    // versioned import
    if (rmw.is1710) {

    } else {
        var BlockPos = Packages.net.minecraft.util.math.BlockPos
    }

    /////////////////////////////////////

    /**
     * net.minecraft.world.Worldのラッパー
     * @param mcWorld world of minecraft
     * @returns {WWorld}
     * @constructor
     */
    function WWorld(mcWorld) {
        if (!(this instanceof WWorld)) {
            NGTLog.debug("you should use World as constructor")
            return new WWorld(mcWorld)
        }

        /**
         * net.minecraft.world.World本体
         */
        this.__real__ = mcWorld
    }

    /**
     * ブロックを取得する
     * @param x {number} x
     * @param y {number} y
     * @param z {number} z
     * @this WWorld
     * @return {WBlock}
     */
    WWorld.prototype.getBlock = __rtm_mc_wrapper__.versioned_value(
        /**
         * ブロックを取得する
         * @param x {number} x
         * @param y {number} y
         * @param z {number} z
         * @this WWorld
         * @return {WBlock}
         */
        function (x, y, z) {
            var block = this.__real__.func_147439_a(x, y, z) // World.getBlock
            var meta = this.__real__.func_72805_g(x, y, z) // World.getBlockMetadata
            return new WBlock({block: block, meta: meta})
        },
        function (x, y, z) {
            var blockState = this.__real__.func_180495_p(new BlockPos(x, y, z)) // World.getBlockState
            var block = blockState.func_177230_c() // IBlockState.getBlock()
            // noinspection JSUnresolvedFunction
            var name = block.getRegistryName().toString() // getRegistryName by forge
            var meta = block.func_176201_c(blockState)//Block.getMetaFromState
            return new WBlock({name: name, meta: meta})
        }
    )

    WWorld.prototype.setBlock = __rtm_mc_wrapper__.versioned_value(
        /**
         * ブロックを保存する
         * @param x {number} x
         * @param y {number} y
         * @param z {number} z
         * @param block {WBlockParams}
         * @param [flags] {number} [flags]
         * @this WWorld
         * @return {boolean}
         */
        function (x, y, z, block, flags) {
            return this.__real__.func_180501_a(new BlockPos(x, y, z), WBlock.create(block).makeBlockState(), flags || 3)
            // World.setBlockState(BlockPos, IBlockState, int)
        },
        function (x, y, z, block, flags) {
            var wBlock = WBlock.create(block)
            return this.__real__.func_147465_d(x, y, z, wBlock.__real__, block.meta, flags || 3)
            // World.setBlock(int, int, int, Block, int, int): boolean
        }
    )

    WWorld.prototype.getTileEntity = __rtm_mc_wrapper__.versioned_value(
        /**
         * TileEntityの情報を取得する。
         * 
         * @param x {number} x
         * @param y {number} y
         * @param z {number} z
         * @this WWorld
         * @return {WTileEntity}
         */
        function (x, y, z) {
            var tile = this.__real__.func_147438_o(x, y, z) // World.getTileEntity
            if (tile == null) return null;
            return new WTileEntity(tile);
        },
        function (x, y, z) {
            var tile = this.__real__.func_175625_s(new BlockPos(x, y, z)) // World.getTileEntity
            if (tile == null) return null;
            return new WTileEntity(tile);
        }
    )

    WWorld.prototype.setTileEntity = __rtm_mc_wrapper__.versioned_value(
        /**
         * TileEntityを保存する
         * @param x {number} x
         * @param y {number} y
         * @param z {number} z
         * @param tile {WTileEntity}
         * @this WWorld
         */
        function (x, y, z, tile) {
            this.__real__.func_147455_a(x, y, z, tile.tile)
        },
        function (x, y, z, tile) {
            this.__real__.func_175690_a(new BlockPos(x, y, z), tile.tile) // World.setTileEntity
        }
    )

    var worldMap = new Packages.java.util.WeakHashMap();
    var makeWorld = function (key) { return new WWorld(key) };

    /**
     * @param world
     * @return {null|WWorld}
     */
    WWorld.wrap = function(world) {
        if (world == null) return null;
        return worldMap.computeIfAbsent(world, makeWorld)
    }

    global.WWorld = WWorld;
})
