# Splitter
Type `!split` in-game to calculate the split of the current coins or platinum tokens in you inventory. Or you can use it to see how much the split of an item would be by typing `!split <item-name>`.

![Demonstrating the results of using the plugin](https://i.imgur.com/YV08F8I.png)

The calcutation is floored. This means that 10/3=3.

## Usage
* Using `!split` will do the calculation based on the default number to split by (which can be changed in the plugins configuration).
* Using `!split 3` will split the value by 3 regardless of the default value. Any number greater than 0 can be used. **MUST be an integer**.
* Using `!split arcane` will get the GE price using the same logic as `!price` and split it with the default number to split by.
* Using `!split arcane 5` will get the GE price using the same logic as `!price` and split it 5 ways.

![Showing the results of splitting arcane 5-ways](https://i.imgur.com/YxKfEbd.png, "Spltting arcane 5-ways")

If you don't have any coin(s) or platinum token(s) in your inventory the message "No Coin(s) or Platinum token(s) in the inventory." will be shown.

![Showing the text when there are no coins or platinum tokens in the inventory](https://i.imgur.com/9zxizQL.png)

If you have both coin(s) and platinum token(s) the coin(s) will be ignored and the division will only take the platinum tokens in to account.

## Settings
![Showing how the settings of the plugin looks](https://i.imgur.com/XJ69gIg.png)
