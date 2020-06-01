import React, { useEffect } from 'react';
import { RouteComponentProps } from '@reach/router';
import { useDispatch, useSelector } from 'react-redux';
import { fetchMatches, selectMatchItems } from './matchingSlice';
import {
  Card,
  CardActionArea,
  CardContent,
  CardMedia,
  createStyles,
  Icon,
  makeStyles,
  Paper,
  Typography,
} from '@material-ui/core';
import { ScrapeItemType } from '../../types';

const useStyles = makeStyles((theme) =>
  createStyles({
    wrapper: {
      display: 'flex',
      flexWrap: 'wrap',
    },
    card: {
      width: 342,
      margin: 4,
    },
    fallbackImageWrapper: {
      display: 'flex',
      width: '100%',
      height: '100%',
      color: theme.palette.grey[500],
      backgroundColor: theme.palette.grey[300],
      fontSize: '10rem',
    },
    fallbackImageIcon: {
      alignSelf: 'center',
      margin: '0 auto',
      display: 'inline-block',
    },
  }),
);

type Props = RouteComponentProps;

export default function Matching(props: Props) {
  const classes = useStyles();
  const dispatch = useDispatch();
  const items = useSelector(selectMatchItems);

  useEffect(() => {
    dispatch(fetchMatches());
  }, []);

  const cards = items.slice(0, 10).map((item) => {
    const poster = (item.potential.images || []).find(
      (image) => image.image_type === 'poster',
    );

    const ttLink = `https://qa.teletracker.tv/${item.potential.type}s/${item.potential.id}`;
    const scrapedPoster = item.scraped.item.posterImageUrl;

    return (
      <Paper
        elevation={3}
        style={{
          display: 'flex',
          justifyContent: 'space-around',
          flex: 1,
          margin: 8,
          padding: 8,
        }}
        key={item.id}
      >
        <Card className={classes.card}>
          <CardActionArea component="a" href={ttLink} target="_blank">
            <CardMedia
              component="img"
              height="500"
              image={
                poster?.id ? `https://image.tmdb.org/t/p/w342${poster!.id}` : ''
              }
            />
            <CardContent>
              <Typography gutterBottom variant="h5" component="h2">
                Potential - {item.potential.title}
              </Typography>
            </CardContent>
          </CardActionArea>
        </Card>
        <Card className={classes.card}>
          <CardActionArea
            component="a"
            href={item.scraped.item.url}
            target="_blank"
          >
            {scrapedPoster ? (
              <CardMedia component="img" height="500" image={scrapedPoster} />
            ) : (
              <div
                className={classes.fallbackImageWrapper}
                style={{
                  width: '100%',
                  objectFit: 'cover',
                  height: 500,
                }}
              >
                <Icon className={classes.fallbackImageIcon} fontSize="inherit">
                  broken_image
                </Icon>
              </div>
            )}

            <CardContent>
              <Typography gutterBottom variant="h5" component="h2">
                Scraped - {item.scraped.item.title}
              </Typography>
            </CardContent>
          </CardActionArea>
        </Card>
      </Paper>
    );
  });

  return <div className={classes.wrapper}>{cards}</div>;
}
