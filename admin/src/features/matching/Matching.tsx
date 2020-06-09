import React, { useCallback, useEffect, useState } from 'react';
import { RouteComponentProps } from '@reach/router';
import { useDispatch, useSelector } from 'react-redux';
import { fetchMatchesAsync, updatePotentialMatchAsync } from './matchingSlice';
import {
  Button,
  ButtonGroup,
  Card,
  CardActionArea,
  CardContent,
  CardMedia,
  Chip,
  Grid,
  createStyles,
  Icon,
  makeStyles,
  Paper,
  Typography,
} from '@material-ui/core';
import { PotentialMatchState, ScrapeItemType } from '../../types';
import { SearchMatchSort } from '../../util/apiClient';
import { useDebouncedCallback } from 'use-debounce';
import { RootState } from '../../app/store';
import InfiniteScroll from 'react-infinite-scroller';
import { Link } from '@reach/router';

const useStyles = makeStyles((theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.5, 0.5, 0.5, 0),
    },
    wrapper: {
      display: 'flex',
      flexWrap: 'wrap',
    },
    buttonWrapper: {
      flexDirection: 'row',
      display: 'flex',
      flexGrow: 1,
      height: 35,
    },
    button: {
      margin: 4,
    },
    card: {
      width: '50%',
      margin: 4,
    },
    cardDescription: {
      height: 'auto',
      overflow: 'scroll',
    },
    cardWrapper: {
      display: 'flex',
      flexGrow: 1,
      height: 600,
    },
    dataType: {
      color: '#fff',
      backgroundColor: '#3f51b5',
      marginLeft: -16,
      paddingLeft: 8,
    },
    fallbackImageWrapper: {
      display: 'flex',
      color: theme.palette.grey[500],
      backgroundColor: theme.palette.grey[300],
      fontSize: '10rem',
      width: '100%',
      objectFit: 'cover',
      height: 275,
    },
    fallbackImageIcon: {
      alignSelf: 'center',
      margin: '0 auto',
      display: 'inline-block',
    },
    paper: {
      display: 'flex',
      justifyContent: 'space-around',
      flex: 1,
      margin: 8,
      padding: 8,
      flexWrap: 'wrap',
      height: 650,
    },
    navigationType: {
      marginBottom: theme.spacing(2),
    },
    totalItemsWrapper: {
      textAlign: 'center',
      marginBottom: theme.spacing(2),
    },
  }),
);

interface Props extends RouteComponentProps {
  filterType?: PotentialMatchState;
  networkScraper?: ScrapeItemType | 'all';
}

export default function Matching(props: Props) {
  const classes = useStyles();
  const dispatch = useDispatch();
  const [status, setStatus] = useState(props.filterType);
  const [networkScraper, setNetworkScraper] = useState(props.networkScraper);

  const items = useSelector((state: RootState) =>
    state.matching.items.filter((item) => item.state === status),
  );
  const itemsLoading = useSelector(
    (state: RootState) => state.matching.loading.matches,
  );
  const bookmark = useSelector((state: RootState) => state.matching.bookmark);
  const totalItems = useSelector(
    (state: RootState) => state.matching.totalHits,
  );

  useEffect(() => {
    dispatch(
      fetchMatchesAsync({
        matchState: status,
        scraperItemType: networkScraper === 'all' ? undefined : networkScraper,
        sort:
          status === PotentialMatchState.Unmatched
            ? SearchMatchSort.PotentialMatchPopularity
            : SearchMatchSort.LastStateChange,
      }),
    );
  }, [networkScraper, status]);

  const markAsNonMatch = useCallback((id: string) => {
    dispatch(
      updatePotentialMatchAsync({
        id,
        state: PotentialMatchState.NonMatch,
      }),
    );
  }, []);

  const markAsMatch = useCallback((id: string) => {
    dispatch(
      updatePotentialMatchAsync({
        id,
        state: PotentialMatchState.Matched,
      }),
    );
  }, []);

  const [loadMoreResults] = useDebouncedCallback(() => {
    if (!itemsLoading) {
      dispatch(
        fetchMatchesAsync({
          bookmark: bookmark,
          matchState: status,
          scraperItemType:
            networkScraper === 'all' ? undefined : networkScraper,
          sort:
            status === PotentialMatchState.Unmatched
              ? SearchMatchSort.PotentialMatchPopularity
              : SearchMatchSort.LastStateChange,
        }),
      );
    }
  }, 100);

  const cards = items.map((item) => {
    const poster = (item.potential.images || []).find(
      (image) => image.image_type === 'poster',
    );

    const ttLink = `https://qa.teletracker.tv/${item.potential.type}s/${item.potential.id}`;
    const scrapedPoster = item.scraped.item.posterImageUrl;

    return (
      <Grid item xs={4} key={item.id}>
        <Paper elevation={3} className={classes.paper}>
          <div className={classes.cardWrapper}>
            <Card className={classes.card}>
              <CardActionArea component="a" href={ttLink} target="_blank">
                <CardMedia
                  component="img"
                  height="275"
                  image={
                    poster?.id
                      ? `https://image.tmdb.org/t/p/w342${poster!.id}`
                      : ''
                  }
                />
                <CardContent>
                  <Typography
                    gutterBottom
                    variant="h5"
                    component="h2"
                    className={classes.dataType}
                  >
                    Potential
                  </Typography>
                  <Typography gutterBottom variant="h5" component="h2">
                    {item.potential.title}
                  </Typography>
                  {item.potential.type && (
                    <Chip
                      label={item.potential.type}
                      className={classes.chip}
                    />
                  )}
                  {item?.potential?.release_date && (
                    <Chip
                      label={item?.potential?.release_date?.substring(0, 4)}
                      className={classes.chip}
                    />
                  )}
                  <Typography
                    variant="body2"
                    color="textSecondary"
                    component="p"
                    className={classes.cardDescription}
                  >
                    {item.potential?.description}
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
                  <CardMedia
                    component="img"
                    height="275"
                    image={scrapedPoster}
                  />
                ) : (
                  <div className={classes.fallbackImageWrapper}>
                    <Icon
                      className={classes.fallbackImageIcon}
                      fontSize="inherit"
                    >
                      broken_image
                    </Icon>
                  </div>
                )}

                <CardContent>
                  <Typography
                    gutterBottom
                    variant="h5"
                    component="h2"
                    className={classes.dataType}
                  >
                    Scraped
                  </Typography>
                  <Typography gutterBottom variant="h5" component="h2">
                    {item.scraped.item.title}
                  </Typography>
                  {item.scraped.item.itemType && (
                    <Chip
                      label={item.scraped.item.itemType}
                      className={classes.chip}
                    />
                  )}
                  {item.scraped.item.releaseYear && (
                    <Chip
                      label={item.scraped.item.releaseYear}
                      className={classes.chip}
                    />
                  )}
                  {item.scraped.item.network && (
                    <Chip
                      label={item.scraped.item.network}
                      className={classes.chip}
                    />
                  )}
                  <Typography
                    variant="body2"
                    color="textSecondary"
                    component="p"
                    className={classes.cardDescription}
                  >
                    {item.scraped.item.description}
                  </Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          </div>
          <div className={classes.buttonWrapper}>
            <Button
              size="small"
              color="secondary"
              variant="contained"
              fullWidth
              className={classes.button}
              onClick={() => markAsNonMatch(item.id)}
            >
              Not a Match
            </Button>
            <Button
              size="small"
              color="primary"
              variant="contained"
              fullWidth
              className={classes.button}
              onClick={() => markAsMatch(item.id)}
            >
              Hooray, a Match!
            </Button>
          </div>
        </Paper>
      </Grid>
    );
  });

  return (
    <React.Fragment>
      <ButtonGroup
        color="primary"
        aria-label="contained primary button group"
        className={classes.navigationType}
      >
        <Button
          variant={
            status === PotentialMatchState.Unmatched ? 'contained' : undefined
          }
          component={Link}
          to={`/matching/${PotentialMatchState.Unmatched}/${networkScraper}`}
          onClick={() => setStatus(PotentialMatchState.Unmatched)}
        >
          Pending Approval
        </Button>
        <Button
          variant={
            status === PotentialMatchState.Matched ? 'contained' : undefined
          }
          component={Link}
          to={`/matching/${PotentialMatchState.Matched}/${networkScraper}`}
          onClick={() => setStatus(PotentialMatchState.Matched)}
        >
          Approved
        </Button>
        <Button
          variant={
            status === PotentialMatchState.NonMatch ? 'contained' : undefined
          }
          component={Link}
          to={`/matching/${PotentialMatchState.NonMatch}/${networkScraper}`}
          onClick={() => setStatus(PotentialMatchState.NonMatch)}
        >
          Rejected
        </Button>
      </ButtonGroup>
      <ButtonGroup
        color="primary"
        aria-label="contained primary button group"
        className={classes.navigationType}
      >
        <Button
          variant={!networkScraper ? 'contained' : undefined}
          component={Link}
          to={`/matching/${status}/all`}
          onClick={() => setNetworkScraper(undefined)}
        >
          All
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.HuluCatalog
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.HuluCatalog}`}
          onClick={() => setNetworkScraper(ScrapeItemType.HuluCatalog)}
        >
          Hulu
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.NetflixCatalog
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.NetflixCatalog}`}
          onClick={() => setNetworkScraper(ScrapeItemType.NetflixCatalog)}
        >
          Netflix
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.NetflixOriginalsArriving
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.NetflixOriginalsArriving}`}
          onClick={() =>
            setNetworkScraper(ScrapeItemType.NetflixOriginalsArriving)
          }
        >
          Netflix Originals Arriving
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.DisneyPlusCatalog
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.DisneyPlusCatalog}`}
          onClick={() => setNetworkScraper(ScrapeItemType.DisneyPlusCatalog)}
        >
          Disney+
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.HboMaxCatalog
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.HboMaxCatalog}`}
          onClick={() => setNetworkScraper(ScrapeItemType.HboMaxCatalog)}
        >
          HBO Max
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.HboChanges
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.HboChanges}`}
          onClick={() => setNetworkScraper(ScrapeItemType.HboChanges)}
        >
          HBO Changes
        </Button>
        <Button
          variant={
            networkScraper === ScrapeItemType.HboCatalog
              ? 'contained'
              : undefined
          }
          component={Link}
          to={`/matching/${status}/${ScrapeItemType.HboCatalog}`}
          onClick={() => setNetworkScraper(ScrapeItemType.HboCatalog)}
        >
          HBO
        </Button>
      </ButtonGroup>
      <div className={classes.totalItemsWrapper}>
        <Typography
          variant="body2"
          color="textSecondary"
          component="p"
        >{`Showing ${items.length} of ${totalItems} total items`}</Typography>
      </div>
      <InfiniteScroll
        pageStart={0}
        loadMore={loadMoreResults}
        hasMore={
          totalItems !== undefined
            ? items.length < totalItems
            : bookmark === undefined
        }
        useWindow
        threshold={300}
      >
        <Grid container spacing={3}>
          <div className={classes.wrapper}>{cards}</div>
        </Grid>
      </InfiniteScroll>
    </React.Fragment>
  );
}
