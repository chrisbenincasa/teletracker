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

// Format Run Time
export const formatRuntime = (runtime: number | number[], type: string) => {
  if (type === 'movie') {
    const hours = Math.floor(Number(runtime) / 60);
    const minutes = Number(runtime) % 60;

    if (hours > 0 && minutes > 0) {
      return `${hours}h ${minutes}m`;
    } else if (hours > 0 && minutes === 0) {
      return `${hours}h`;
    } else if (hours === 0 && minutes > 0) {
      return `${minutes}m`;
    } else {
      return null;
    }
  } else if (type === 'show') {
    const max = Math.max(...runtime);
    const min = Math.min(...runtime);

    if (max === min) {
      return `Episode Length: ${max}m`;
    } else {
      return `Episode Length: ${min}-${max}m`;
    }
  } else {
    return null;
  }
};

/*
    Truncate text and append ellipsis
*/
export const truncateText = (text: string, lengthLimit: number) => {
  if (text.length > lengthLimit) {
    return `${text.substr(0, lengthLimit - 4).trim()}...`;
  }

  return text;
};
