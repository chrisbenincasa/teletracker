/*
    Parse initials from multi-word string
    Example 1: "Season 12" becomes "S12"
    Example 2: "Marc Maron" becomes "MM"
*/
export const parseInitials = (name: string, type: string) => {
  if (type === 'season') {
    var nameSplit = name.split(' ');
    nameSplit[0] = nameSplit[0].substring(0, 1);
    return nameSplit.join('');
  } else {
    var matches = name.match(/\b(\w)/g);
    return matches!.join('');
  }
};
