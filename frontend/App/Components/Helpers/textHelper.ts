/*
    Parse initials from multi-word string
    Example 1: "Season 12" becomes "S12"
    Example 2: "Marc Maron" becomes "MM"
*/
export const parseInitials = (name: string, type: string) => {
    if (type === 'season') {
        var nameSplit = name.split(" ");
        nameSplit[0] = nameSplit[0].substring(0,1);
        return nameSplit.join('');
    } else {
        var matches = name.match(/\b(\w)/g);
        return matches.join(''); 
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

/*
    Valid email
*/
export const validateEmail = (email: string) => {
    /*
        this regex conforms to the official standard RFC 2822(https://tools.ietf.org/html/rfc2822#section-3.4.1)
        - must use letters a-z, numbers 0-9, or common special chars !#$%&'*+/=?^_`{|}~-
        - must have at least 1 matching character before '@' symbol
        - must have '@' symbol
        - excludes '"' double quotes and ':' colons and ';' semicolons
        - '@' must be followed by letters a-z or numbers 0-9
        - can contain '-' but must be surrounded by letters a-0 or numbers 0-9
        - must use at least one '.' character
        - does not exclude any specific domains
    */

    const emailRegex = /(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])/;

    // returns true if the email is valid
    return emailRegex.test(email);
};