# Splitter
Type `!split` in-game to calculate the split of the current coins or platinum tokens in you inventory.

![Demonstrating the results of using the plugin](https://i.imgur.com/yUKN7Hx.png)

The calcutation is floored. This means that 10/3=3.

## Usage
* Using `!split` will do the calculation based on the default number to split by (which can be changed in the plugins configuration).
* Using `!split 3` will split the value by 3 regardless of the default value. Any number greater than 0 can be used. **MUST be an integer**.

If you don't have any coin(s) or platinum token(s) in your inventory the message "No Coin(s) or Platinum token(s) in the inventory." will be shown.
![Showing the text when there are no coins or platinum tokens in the inventory](https://i.imgur.com/9zxizQL.png)

If you have both coin(s) and platinum token(s) the coin(s) will be ignored and the division will only take the platinum tokens in to account.

## Settings
![Showing how the settings of the plugin looks](https://i.imgur.com/XJ69gIg.png)
