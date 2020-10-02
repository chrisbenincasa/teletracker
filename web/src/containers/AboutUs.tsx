import React from 'react';
import { createStyles, makeStyles, Theme } from '@material-ui/core';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    wrapper: {
      display: 'flex',
      flexDirection: 'column',
      width: '50%',
      margin: '0 auto',
    },
  }),
);

export default function About() {
  const classes = useStyles();

  return (
    <div className={classes.wrapper}>
      <h1>About Us</h1>
      <p>This is a story all about how...</p>
    </div>
  );
}
