import { Button, makeStyles } from '@material-ui/core';
import { List as ListIcon } from '@material-ui/icons';
import React from 'react';
import { Item, itemBelongsToLists } from '../types/v2/Item';

const useStyles = makeStyles(theme => ({
  button: {
    marginTop: theme.spacing(1),
    width: '100% !important',
    [theme.breakpoints.down('xs')]: {
      fontSize: '0.55rem',
    },
    fontSIze: '2rem',
  },
  buttonIcon: {
    marginRight: theme.spacing(1),
    [theme.breakpoints.down('sm')]: {
      fontSize: '1rem',
    },
    fontSIze: '2rem',
  },
  itemCTA: {
    width: '100%',
    whiteSpace: 'nowrap',
  },
}));

interface Props {
  itemDetail?: Item;
  onClick: () => void;
  style?: object;
  cta?: string;
}

export default function ManageTrackingButton(props: Props) {
  const classes = useStyles();

  const belongsToLists: number[] =
    props && props.itemDetail ? itemBelongsToLists(props.itemDetail) : [];

  let trackingCTA = belongsToLists ? 'Manage Tracking' : 'Add to List';

  return (
    <div className={classes.itemCTA} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        aria-label={props.cta || trackingCTA}
        onClick={props.onClick}
        className={classes.button}
        startIcon={<ListIcon className={classes.buttonIcon} />}
      >
        {props.cta || trackingCTA}
      </Button>
    </div>
  );
}
