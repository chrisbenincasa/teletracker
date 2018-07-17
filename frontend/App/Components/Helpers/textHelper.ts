export const parseInitials = (name: string) => {
    var matches = name.match(/\b(\w)/g);
    return matches.join(''); 
};