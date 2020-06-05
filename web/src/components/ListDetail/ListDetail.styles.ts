import { createStyles, makeStyles, Theme } from '@material-ui/core';

const classes = makeStyles((theme: Theme) =>
  createStyles({
    listHeader: {
      margin: theme.spacing(2, 0),
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listName: {
      display: 'flex',
      textDecoration: 'none',
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: theme.palette.text.primary,
      },
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
    icon: {
      margin: theme.spacing(0, 1),
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      padding: theme.spacing(0, 2),
      width: '100%',
    },
    noContent: {
      margin: theme.spacing(5),
      width: '100%',
    },
    root: {
      display: 'flex',
      flexGrow: 1,
    },
    textField: {
      margin: theme.spacing(0, 1),
      width: 200,
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
    urlField: {
      margin: theme.spacing(1),
    },
  }),
);

export default classes;
